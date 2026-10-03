#include "occt_internal.h"
#include "occt.h"

#include <cmath>
#include <stdexcept>

#include <BRepBuilderAPI_MakeEdge.hxx>
#include <BRepBuilderAPI_MakeVertex.hxx>
#include <BRepBuilderAPI_MakeWire.hxx>
#include <BRepLib.hxx>
#include <GCE2d_MakeSegment.hxx>
#include <GC_MakeArcOfCircle.hxx>
#include <Geom2d_Curve.hxx>
#include <GeomAPI_Interpolate.hxx>
#include <Geom_BSplineCurve.hxx>
#include <Geom_CylindricalSurface.hxx>
#include <Geom_TrimmedCurve.hxx>
#include <Poly_Triangulation.hxx>
#include <TColgp_HArray1OfPnt.hxx>
#include <TopoDS_Edge.hxx>
#include <TopoDS_Vertex.hxx>
#include <gp_Ax3.hxx>
#include <gp_Pnt.hxx>
#include <gp_Pnt2d.hxx>

namespace vg3 {
WireBuilder::WireBuilder() : started_(false), has_edges_(false) {}

void WireBuilder::start(double x, double y, double z) {
    start_point_ = gp_Pnt(x, y, z);
    current_point_ = start_point_;
    BRepBuilderAPI_MakeVertex make_vertex(start_point_);
    start_vertex_ = make_vertex.Vertex();
    current_vertex_ = start_vertex_;
    started_ = true;
}

TopoDS_Vertex WireBuilder::vertex_for(const gp_Pnt& point) {
    if (started_ && point.Distance(start_point_) <= detail::kPointTolerance) {
        return start_vertex_;
    }
    BRepBuilderAPI_MakeVertex make_vertex(point);
    return make_vertex.Vertex();
}

void WireBuilder::add_edge(const TopoDS_Edge& edge) {
    wire_.Add(edge);
    if (!wire_.IsDone()) {
        throw std::runtime_error("contour edges do not connect into a wire");
    }
    has_edges_ = true;
}

void WireBuilder::line(double x, double y, double z) {
    const gp_Pnt point(x, y, z);
    const TopoDS_Vertex next = vertex_for(point);
    BRepBuilderAPI_MakeEdge make_edge(current_vertex_, next);
    if (!make_edge.IsDone()) {
        throw std::runtime_error("cannot build a straight edge");
    }
    add_edge(make_edge.Edge());
    current_vertex_ = next;
    current_point_ = point;
}

void WireBuilder::arc(
    double via_x,
    double via_y,
    double via_z,
    double to_x,
    double to_y,
    double to_z
) {
    const gp_Pnt via(via_x, via_y, via_z);
    const gp_Pnt to(to_x, to_y, to_z);
    GC_MakeArcOfCircle arc(current_point_, via, to);
    if (!arc.IsDone()) {
        throw std::runtime_error("cannot build an arc through the given points");
    }
    const TopoDS_Vertex next = vertex_for(to);
    BRepBuilderAPI_MakeEdge make_edge(arc.Value(), current_vertex_, next);
    if (!make_edge.IsDone()) {
        throw std::runtime_error("cannot build an arc edge");
    }
    add_edge(make_edge.Edge());
    current_vertex_ = next;
    current_point_ = to;
}

void WireBuilder::spline(rust::Slice<const double> points) {
    if (points.size() < 3 || points.size() % 3 != 0) {
        throw std::runtime_error("a spline requires at least one point after the start");
    }
    const std::size_t count = points.size() / 3;
    Handle(TColgp_HArray1OfPnt) array =
        new TColgp_HArray1OfPnt(1, static_cast<Standard_Integer>(count + 1));
    array->SetValue(1, current_point_);
    for (std::size_t index = 0; index < count; ++index) {
        array->SetValue(
            static_cast<Standard_Integer>(index + 2),
            gp_Pnt(points[index * 3], points[index * 3 + 1], points[index * 3 + 2])
        );
    }
    GeomAPI_Interpolate interpolation(array, Standard_False, detail::kPointTolerance);
    interpolation.Perform();
    if (!interpolation.IsDone()) {
        throw std::runtime_error("cannot interpolate a spline through the given points");
    }
    const gp_Pnt end = array->Value(static_cast<Standard_Integer>(count + 1));
    const TopoDS_Vertex next = vertex_for(end);
    BRepBuilderAPI_MakeEdge make_edge(interpolation.Curve(), current_vertex_, next);
    if (!make_edge.IsDone()) {
        throw std::runtime_error("cannot build a spline edge");
    }
    add_edge(make_edge.Edge());
    current_vertex_ = next;
    current_point_ = end;
}

void WireBuilder::helix(double pitch, double height, bool right_handed) {
    constexpr double kPi = 3.14159265358979323846;
    if (pitch <= 0.0 || height <= 0.0) {
        throw std::runtime_error("helix requires a positive pitch and height");
    }
    const double radius = std::sqrt(
        current_point_.X() * current_point_.X() + current_point_.Y() * current_point_.Y()
    );
    if (radius <= detail::kPointTolerance) {
        throw std::runtime_error("helix requires a start point off the Z axis");
    }
    const double phase = std::atan2(current_point_.Y(), current_point_.X());
    const double base = current_point_.Z();
    const double turns = height / pitch;
    const double direction = right_handed ? 1.0 : -1.0;

    Handle(Geom_CylindricalSurface) surface =
        new Geom_CylindricalSurface(gp_Ax3(gp_Pnt(0.0, 0.0, 0.0), gp_Dir(0.0, 0.0, 1.0)), radius);

    const auto add_segment = [&](double u0, double v0, double u1, double v1) {
        GCE2d_MakeSegment segment(gp_Pnt2d(u0, v0), gp_Pnt2d(u1, v1));
        BRepBuilderAPI_MakeEdge make_edge(segment.Value(), surface);
        if (!make_edge.IsDone()) {
            throw std::runtime_error("cannot build a helix edge");
        }
        add_edge(make_edge.Edge());
    };

    const int whole_turns = static_cast<int>(std::floor(turns));
    for (int turn = 0; turn < whole_turns; ++turn) {
        add_segment(
            phase + direction * 2.0 * kPi * turn,
            base + pitch * turn,
            phase + direction * 2.0 * kPi * (turn + 1),
            base + pitch * (turn + 1)
        );
    }
    if (turns - static_cast<double>(whole_turns) > 1e-9) {
        add_segment(
            phase + direction * 2.0 * kPi * whole_turns,
            base + pitch * whole_turns,
            phase + direction * 2.0 * kPi * turns,
            base + height
        );
    }

    const gp_Pnt end(
        radius * std::cos(phase + direction * 2.0 * kPi * turns),
        radius * std::sin(phase + direction * 2.0 * kPi * turns),
        base + height
    );
    BRepBuilderAPI_MakeVertex make_vertex(end);
    current_vertex_ = make_vertex.Vertex();
    current_point_ = end;
}

std::unique_ptr<Shape> WireBuilder::finish(bool closed) {
    if (!started_) {
        throw std::runtime_error("wire has no start point");
    }
    if (closed && !current_vertex_.IsSame(start_vertex_)) {
        BRepBuilderAPI_MakeEdge make_edge(current_vertex_, start_vertex_);
        if (!make_edge.IsDone()) {
            throw std::runtime_error("cannot close the contour");
        }
        add_edge(make_edge.Edge());
    }
    if (!has_edges_) {
        throw std::runtime_error("contour has no edges");
    }
    const TopoDS_Shape shape = wire_.Shape();
    BRepLib::BuildCurves3d(shape);
    detail::ensure_valid(shape);
    return std::make_unique<Shape>(shape);
}

std::unique_ptr<WireBuilder> new_wire_builder() {
    return std::make_unique<WireBuilder>();
}
}  // namespace vg3
