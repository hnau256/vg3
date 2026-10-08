#include "occt_internal.h"
#include "occt.h"

#include <stdexcept>

#include <BRepAdaptor_Curve.hxx>
#include <BRepGProp.hxx>
#include <GeomAbs_CurveType.hxx>
#include <GProp_GProps.hxx>
#include <TopExp.hxx>
#include <TopExp_Explorer.hxx>
#include <TopTools_IndexedDataMapOfShapeListOfShape.hxx>
#include <TopoDS.hxx>
#include <gp_Pnt.hxx>
#include <gp_Vec.hxx>

namespace vg3 {

namespace {

rust::Vec<double> edge_data(const TopoDS_Edge& edge) {
    BRepAdaptor_Curve curve(edge);
    const GeomAbs_CurveType curve_type = curve.GetType();

    double type_code = 8.0;
    switch (curve_type) {
        case GeomAbs_Line: type_code = 0.0; break;
        case GeomAbs_Circle: type_code = 1.0; break;
        case GeomAbs_Ellipse: type_code = 2.0; break;
        case GeomAbs_Hyperbola: type_code = 3.0; break;
        case GeomAbs_Parabola: type_code = 4.0; break;
        case GeomAbs_BezierCurve: type_code = 5.0; break;
        case GeomAbs_BSplineCurve: type_code = 6.0; break;
        case GeomAbs_OffsetCurve: type_code = 7.0; break;
        case GeomAbs_OtherCurve: type_code = 8.0; break;
    }

    double radius = 0.0;
    if (curve_type == GeomAbs_Circle) {
        radius = curve.Circle().Radius();
    } else if (curve_type == GeomAbs_Ellipse) {
        radius = curve.Ellipse().MajorRadius();
    }

    GProp_GProps properties;
    BRepGProp::LinearProperties(edge, properties);

    const double first = curve.FirstParameter();
    const double last = curve.LastParameter();
    gp_Pnt middle;
    gp_Vec tangent;
    curve.D1(0.5 * (first + last), middle, tangent);
    const double magnitude = tangent.Magnitude();
    if (magnitude > 0.0) {
        tangent /= magnitude;
    }

    const gp_Pnt start = curve.Value(first);
    const gp_Pnt end = curve.Value(last);

    const detail::Bounds bounds = detail::bounds(edge);

    rust::Vec<double> data;
    data.push_back(properties.Mass());          // 0: length
    data.push_back(type_code);                  // 1: curve type
    data.push_back(tangent.X());                // 2,3,4: unit tangent at the middle
    data.push_back(tangent.Y());
    data.push_back(tangent.Z());
    data.push_back(radius);                     // 5: radius
    data.push_back(start.X());                  // 6,7,8: start
    data.push_back(start.Y());
    data.push_back(start.Z());
    data.push_back(end.X());                    // 9,10,11: end
    data.push_back(end.Y());
    data.push_back(end.Z());
    data.push_back(middle.X());                 // 12,13,14: center (mid parameter)
    data.push_back(middle.Y());
    data.push_back(middle.Z());
    data.push_back(bounds.min.X());             // 15,16,17: bbox min
    data.push_back(bounds.min.Y());
    data.push_back(bounds.min.Z());
    data.push_back(bounds.max.X());             // 18,19,20: bbox max
    data.push_back(bounds.max.Y());
    data.push_back(bounds.max.Z());
    return data;
}

}  // namespace

std::size_t solid_edge_count(const Shape& shape, std::size_t solid_index) {
    const TopoDS_Shape solid = detail::nth_solid(shape.topods(), solid_index);
    TopTools_IndexedDataMapOfShapeListOfShape edge_faces;
    TopExp::MapShapesAndAncestors(solid, TopAbs_EDGE, TopAbs_FACE, edge_faces);
    std::size_t count = 0;
    for (TopExp_Explorer explorer(solid, TopAbs_EDGE); explorer.More(); explorer.Next()) {
        if (!detail::is_seam_edge(TopoDS::Edge(explorer.Current()), edge_faces)) {
            ++count;
        }
    }
    return count;
}

rust::Vec<double> solid_edge_data(
    const Shape& shape,
    std::size_t solid_index,
    std::size_t edge_index
) {
    const TopoDS_Shape solid = detail::nth_solid(shape.topods(), solid_index);
    TopTools_IndexedDataMapOfShapeListOfShape edge_faces;
    TopExp::MapShapesAndAncestors(solid, TopAbs_EDGE, TopAbs_FACE, edge_faces);
    std::size_t current = 0;
    for (TopExp_Explorer explorer(solid, TopAbs_EDGE); explorer.More(); explorer.Next()) {
        const TopoDS_Edge edge = TopoDS::Edge(explorer.Current());
        if (detail::is_seam_edge(edge, edge_faces)) {
            continue;
        }
        if (current == edge_index) {
            return edge_data(edge);
        }
        ++current;
    }
    throw std::runtime_error("edge index out of range");
}
}  // namespace vg3
