#include "occt.h"

#include <cmath>
#include <stdexcept>
#include <string>

#include <BRepAdaptor_Curve.hxx>
#include <BRepAlgoAPI_BooleanOperation.hxx>
#include <BRepAlgoAPI_Common.hxx>
#include <BRepAlgoAPI_Cut.hxx>
#include <BRepAlgoAPI_Fuse.hxx>
#include <BRepBuilderAPI_GTransform.hxx>
#include <BRepBuilderAPI_MakeEdge.hxx>
#include <BRepBuilderAPI_MakeFace.hxx>
#include <BRepBuilderAPI_MakeVertex.hxx>
#include <BRepBuilderAPI_Transform.hxx>
#include <BRepCheck_Analyzer.hxx>
#include <BRepFilletAPI_MakeChamfer.hxx>
#include <BRepFilletAPI_MakeFillet.hxx>
#include <BRepGProp.hxx>
#include <BRepMesh_IncrementalMesh.hxx>
#include <GeomAbs_CurveType.hxx>
#include <BRepOffsetAPI_MakePipeShell.hxx>
#include <BRepPrimAPI_MakePrism.hxx>
#include <BRepPrimAPI_MakeRevol.hxx>
#include <BRepAdaptor_CompCurve.hxx>
#include <Bnd_Box.hxx>
#include <BRepBndLib.hxx>
#include <GProp_GProps.hxx>
#include <GC_MakeArcOfCircle.hxx>
#include <GeomAPI_Interpolate.hxx>
#include <Geom_BSplineCurve.hxx>
#include <Geom_TrimmedCurve.hxx>
#include <TColgp_HArray1OfPnt.hxx>
#include <TopoDS.hxx>
#include <TopoDS_Wire.hxx>
#include <BRepPrimAPI_MakeBox.hxx>
#include <BRepPrimAPI_MakeCone.hxx>
#include <BRepPrimAPI_MakeCylinder.hxx>
#include <BRepPrimAPI_MakeSphere.hxx>
#include <BRepPrimAPI_MakeTorus.hxx>
#include <BRepPrimAPI_MakeWedge.hxx>
#include <Standard_Failure.hxx>
#include <StlAPI_Writer.hxx>
#include <TopAbs_ShapeEnum.hxx>
#include <TopExp.hxx>
#include <TopExp_Explorer.hxx>
#include <TopTools_IndexedDataMapOfShapeListOfShape.hxx>
#include <TopTools_ListIteratorOfListOfShape.hxx>
#include <TopTools_ListOfShape.hxx>
#include <TopoDS_Face.hxx>
#include <BRep_Tool.hxx>
#include <TopoDS_Iterator.hxx>
#include <TopoDS_Shape.hxx>
#include <gp_Ax1.hxx>
#include <gp_Ax2.hxx>
#include <gp_Dir.hxx>
#include <gp_GTrsf.hxx>
#include <gp_Pnt.hxx>
#include <gp_Trsf.hxx>
#include <gp_Vec.hxx>

namespace vg3 {

namespace {

constexpr double kPointTolerance = 1e-7;

[[noreturn]] void rethrow_as_std_error(const Standard_Failure& failure) {
    throw std::runtime_error(failure.GetMessageString());
}

void ensure_valid(const TopoDS_Shape& shape) {
    BRepCheck_Analyzer analyzer(shape);
    if (!analyzer.IsValid()) {
        throw std::runtime_error("OpenCASCADE produced an invalid shape");
    }
}

bool solids_only_recursive(const TopoDS_Shape& shape) {
    switch (shape.ShapeType()) {
        case TopAbs_SOLID:
            return true;
        case TopAbs_COMPOUND:
        case TopAbs_COMPSOLID: {
            for (TopoDS_Iterator iterator(shape); iterator.More(); iterator.Next()) {
                if (!solids_only_recursive(iterator.Value())) {
                    return false;
                }
            }
            return true;
        }
        default:
            return false;
    }
}

}  // namespace

Shape::Shape(const TopoDS_Shape& shape) : shape_(shape) {}

const TopoDS_Shape& Shape::topods() const {
    return shape_;
}

CompoundBuilder::CompoundBuilder() {
    builder_.MakeCompound(compound_);
}

void CompoundBuilder::push(const Shape& shape) {
    builder_.Add(compound_, shape.topods());
}

std::unique_ptr<Shape> CompoundBuilder::finish() {
    return std::make_unique<Shape>(compound_);
}

std::unique_ptr<CompoundBuilder> new_compound_builder() {
    return std::make_unique<CompoundBuilder>();
}

std::unique_ptr<Shape> make_box(double width, double length, double height) {
    try {
        BRepPrimAPI_MakeBox maker(width, length, height);
        const TopoDS_Shape shape = maker.Shape();
        ensure_valid(shape);
        return std::make_unique<Shape>(shape);
    } catch (const Standard_Failure& failure) {
        rethrow_as_std_error(failure);
    }
}

std::unique_ptr<Shape> make_sphere(double radius) {
    try {
        BRepPrimAPI_MakeSphere maker(radius);
        const TopoDS_Shape shape = maker.Shape();
        ensure_valid(shape);
        return std::make_unique<Shape>(shape);
    } catch (const Standard_Failure& failure) {
        rethrow_as_std_error(failure);
    }
}

std::unique_ptr<Shape> make_cylinder(double radius, double height) {
    try {
        BRepPrimAPI_MakeCylinder maker(radius, height);
        const TopoDS_Shape shape = maker.Shape();
        ensure_valid(shape);
        return std::make_unique<Shape>(shape);
    } catch (const Standard_Failure& failure) {
        rethrow_as_std_error(failure);
    }
}

std::unique_ptr<Shape> make_cone(double radius_bottom, double radius_top, double height) {
    try {
        BRepPrimAPI_MakeCone maker(radius_bottom, radius_top, height);
        const TopoDS_Shape shape = maker.Shape();
        ensure_valid(shape);
        return std::make_unique<Shape>(shape);
    } catch (const Standard_Failure& failure) {
        rethrow_as_std_error(failure);
    }
}

std::unique_ptr<Shape> make_torus(double major_radius, double minor_radius) {
    try {
        BRepPrimAPI_MakeTorus maker(major_radius, minor_radius);
        const TopoDS_Shape shape = maker.Shape();
        ensure_valid(shape);
        return std::make_unique<Shape>(shape);
    } catch (const Standard_Failure& failure) {
        rethrow_as_std_error(failure);
    }
}

std::unique_ptr<Shape> make_wedge(double width, double length, double height, double top_width) {
    try {
        BRepPrimAPI_MakeWedge maker(width, length, height, top_width);
        const TopoDS_Shape shape = maker.Shape();
        ensure_valid(shape);
        return std::make_unique<Shape>(shape);
    } catch (const Standard_Failure& failure) {
        rethrow_as_std_error(failure);
    }
}

namespace {

std::unique_ptr<Shape> boolean(
    const Shape& a,
    const Shape& b,
    BRepAlgoAPI_BooleanOperation& operation,
    const char* name
) {
    try {
        TopTools_ListOfShape arguments;
        arguments.Append(a.topods());
        operation.SetArguments(arguments);
        TopTools_ListOfShape tools;
        tools.Append(b.topods());
        operation.SetTools(tools);
        operation.SetRunParallel(Standard_False);
        operation.Build();
        if (!operation.IsDone()) {
            throw std::runtime_error(std::string(name) + " did not complete");
        }
        const TopoDS_Shape result = operation.Shape();
        ensure_valid(result);
        return std::make_unique<Shape>(result);
    } catch (const Standard_Failure& failure) {
        rethrow_as_std_error(failure);
    }
}

}  // namespace

std::unique_ptr<Shape> fuse(const Shape& a, const Shape& b) {
    BRepAlgoAPI_Fuse operation;
    return boolean(a, b, operation, "BRepAlgoAPI_Fuse");
}

std::unique_ptr<Shape> cut(const Shape& a, const Shape& b) {
    BRepAlgoAPI_Cut operation;
    return boolean(a, b, operation, "BRepAlgoAPI_Cut");
}

std::unique_ptr<Shape> common(const Shape& a, const Shape& b) {
    BRepAlgoAPI_Common operation;
    return boolean(a, b, operation, "BRepAlgoAPI_Common");
}

std::unique_ptr<Shape> translate(const Shape& shape, double x, double y, double z) {
    try {
        gp_Trsf transformation;
        transformation.SetTranslation(gp_Vec(x, y, z));
        BRepBuilderAPI_Transform builder(shape.topods(), transformation, true);
        const TopoDS_Shape result = builder.Shape();
        ensure_valid(result);
        return std::make_unique<Shape>(result);
    } catch (const Standard_Failure& failure) {
        rethrow_as_std_error(failure);
    }
}

std::unique_ptr<Shape> rotate(
    const Shape& shape,
    double center_x,
    double center_y,
    double center_z,
    double axis_x,
    double axis_y,
    double axis_z,
    double angle
) {
    try {
        gp_Trsf transformation;
        transformation.SetRotation(
            gp_Ax1(gp_Pnt(center_x, center_y, center_z), gp_Dir(axis_x, axis_y, axis_z)),
            angle
        );
        BRepBuilderAPI_Transform builder(shape.topods(), transformation, true);
        const TopoDS_Shape result = builder.Shape();
        ensure_valid(result);
        return std::make_unique<Shape>(result);
    } catch (const Standard_Failure& failure) {
        rethrow_as_std_error(failure);
    }
}

std::unique_ptr<Shape> mirror(
    const Shape& shape,
    double center_x,
    double center_y,
    double center_z,
    double normal_x,
    double normal_y,
    double normal_z
) {
    try {
        gp_Trsf transformation;
        transformation.SetMirror(
            gp_Ax2(gp_Pnt(center_x, center_y, center_z), gp_Dir(normal_x, normal_y, normal_z))
        );
        BRepBuilderAPI_Transform builder(shape.topods(), transformation, true);
        const TopoDS_Shape result = builder.Shape();
        ensure_valid(result);
        return std::make_unique<Shape>(result);
    } catch (const Standard_Failure& failure) {
        rethrow_as_std_error(failure);
    }
}

std::unique_ptr<Shape> scale(const Shape& shape, double x, double y, double z) {
    try {
        gp_GTrsf transformation;
        transformation.SetValue(1, 1, x);
        transformation.SetValue(2, 2, y);
        transformation.SetValue(3, 3, z);
        BRepBuilderAPI_GTransform builder(shape.topods(), transformation, true);
        const TopoDS_Shape result = builder.Shape();
        ensure_valid(result);
        return std::make_unique<Shape>(result);
    } catch (const Standard_Failure& failure) {
        rethrow_as_std_error(failure);
    }
}

std::unique_ptr<Shape> apply_matrix(const Shape& shape, rust::Slice<const double> matrix) {
    try {
        if (matrix.size() != 16) {
            throw std::runtime_error("matrix must contain exactly 16 elements");
        }
        gp_GTrsf transformation;
        for (int row = 1; row <= 3; ++row) {
            for (int column = 1; column <= 4; ++column) {
                transformation.SetValue(row, column, matrix[(row - 1) * 4 + (column - 1)]);
            }
        }
        BRepBuilderAPI_GTransform builder(shape.topods(), transformation, true);
        const TopoDS_Shape result = builder.Shape();
        ensure_valid(result);
        return std::make_unique<Shape>(result);
    } catch (const Standard_Failure& failure) {
        rethrow_as_std_error(failure);
    }
}

bool is_solids_only(const Shape& shape) {
    return solids_only_recursive(shape.topods());
}

std::size_t solid_count(const Shape& shape) {
    std::size_t count = 0;
    for (TopExp_Explorer explorer(shape.topods(), TopAbs_SOLID); explorer.More();
         explorer.Next()) {
        ++count;
    }
    return count;
}

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
    if (started_ && point.Distance(start_point_) <= kPointTolerance) {
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
    GeomAPI_Interpolate interpolation(array, Standard_False, kPointTolerance);
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
    ensure_valid(shape);
    return std::make_unique<Shape>(shape);
}

std::unique_ptr<WireBuilder> new_wire_builder() {
    return std::make_unique<WireBuilder>();
}

namespace {

TopoDS_Face profile_face(const Shape& profile) {
    BRepBuilderAPI_MakeFace make_face(TopoDS::Wire(profile.topods()));
    if (!make_face.IsDone()) {
        throw std::runtime_error("profile is not a closed planar contour");
    }
    return make_face.Face();
}

}  // namespace

std::unique_ptr<Shape> extrude(const Shape& profile, double height) {
    try {
        BRepPrimAPI_MakePrism maker(profile_face(profile), gp_Vec(0.0, 0.0, height));
        if (!maker.IsDone()) {
            throw std::runtime_error("BRepPrimAPI_MakePrism did not complete");
        }
        const TopoDS_Shape shape = maker.Shape();
        ensure_valid(shape);
        return std::make_unique<Shape>(shape);
    } catch (const Standard_Failure& failure) {
        rethrow_as_std_error(failure);
    }
}

std::unique_ptr<Shape> revolve(const Shape& profile, double angle) {
    try {
        BRepPrimAPI_MakeRevol maker(
            profile_face(profile),
            gp_Ax1(gp_Pnt(0.0, 0.0, 0.0), gp_Dir(0.0, 1.0, 0.0)),
            angle
        );
        if (!maker.IsDone()) {
            throw std::runtime_error("BRepPrimAPI_MakeRevol did not complete");
        }
        const TopoDS_Shape shape = maker.Shape();
        ensure_valid(shape);
        return std::make_unique<Shape>(shape);
    } catch (const Standard_Failure& failure) {
        rethrow_as_std_error(failure);
    }
}

std::unique_ptr<Shape> sweep(const Shape& profile, const Shape& spine, bool follow) {
    try {
        const TopoDS_Wire spine_wire = TopoDS::Wire(spine.topods());
        BRepOffsetAPI_MakePipeShell pipe(spine_wire);
        if (follow) {
            pipe.SetMode(Standard_False);
        } else {
            BRepAdaptor_CompCurve curve(spine_wire);
            gp_Pnt point;
            gp_Vec tangent;
            curve.D1(curve.FirstParameter(), point, tangent);
            pipe.SetMode(gp_Ax2(point, gp_Dir(tangent)));
        }
        pipe.SetTransitionMode(BRepBuilderAPI_RightCorner);
        pipe.Add(profile.topods(), Standard_False, Standard_False);
        pipe.Build();
        if (!pipe.IsDone()) {
            throw std::runtime_error("BRepOffsetAPI_MakePipeShell did not complete");
        }
        pipe.MakeSolid();
        const TopoDS_Shape shape = pipe.Shape();
        ensure_valid(shape);
        return std::make_unique<Shape>(shape);
    } catch (const Standard_Failure& failure) {
        rethrow_as_std_error(failure);
    }
}

LoftBuilder::LoftBuilder(bool ruled) : thru_(Standard_True, ruled) {}

void LoftBuilder::add(const Shape& section) {
    thru_.AddWire(TopoDS::Wire(section.topods()));
}

std::unique_ptr<Shape> LoftBuilder::finish() {
    try {
        thru_.Build();
        if (!thru_.IsDone()) {
            throw std::runtime_error("BRepOffsetAPI_ThruSections did not complete");
        }
        const TopoDS_Shape shape = thru_.Shape();
        ensure_valid(shape);
        return std::make_unique<Shape>(shape);
    } catch (const Standard_Failure& failure) {
        rethrow_as_std_error(failure);
    }
}

std::unique_ptr<LoftBuilder> new_loft_builder(bool ruled) {
    return std::make_unique<LoftBuilder>(ruled);
}

rust::Vec<double> bounding_box(const Shape& shape) {
    Bnd_Box box;
    BRepBndLib::Add(shape.topods(), box);
    Standard_Real min_x = 0.0;
    Standard_Real min_y = 0.0;
    Standard_Real min_z = 0.0;
    Standard_Real max_x = 0.0;
    Standard_Real max_y = 0.0;
    Standard_Real max_z = 0.0;
    box.Get(min_x, min_y, min_z, max_x, max_y, max_z);
    rust::Vec<double> result;
    result.push_back(min_x);
    result.push_back(min_y);
    result.push_back(min_z);
    result.push_back(max_x);
    result.push_back(max_y);
    result.push_back(max_z);
    return result;
}

double volume(const Shape& shape) {
    GProp_GProps properties;
    BRepGProp::VolumeProperties(shape.topods(), properties);
    return properties.Mass();
}

namespace {

TopoDS_Shape nth_solid(const TopoDS_Shape& shape, std::size_t index) {
    std::size_t current = 0;
    for (TopExp_Explorer explorer(shape, TopAbs_SOLID); explorer.More();
         explorer.Next(), ++current) {
        if (current == index) {
            return explorer.Current();
        }
    }
    throw std::runtime_error("solid index out of range");
}

bool is_seam_edge(
    const TopoDS_Edge& edge,
    const TopTools_IndexedDataMapOfShapeListOfShape& edge_faces
) {
    if (!edge_faces.Contains(edge)) {
        return false;
    }
    const TopTools_ListOfShape& faces = edge_faces.FindFromKey(edge);
    for (TopTools_ListIteratorOfListOfShape iterator(faces); iterator.More();
         iterator.Next()) {
        if (BRep_Tool::IsClosed(edge, TopoDS::Face(iterator.Value()))) {
            return true;
        }
    }
    return false;
}

rust::Vec<double> edge_data(const TopoDS_Edge& edge) {
    BRepAdaptor_Curve curve(edge);
    const GeomAbs_CurveType curve_type = curve.GetType();

    GProp_GProps properties;
    BRepGProp::LinearProperties(edge, properties);

    double type_code = 2.0;
    if (curve_type == GeomAbs_Line) {
        type_code = 0.0;
    } else if (curve_type == GeomAbs_Circle) {
        type_code = 1.0;
    }

    const double first = curve.FirstParameter();
    const double last = curve.LastParameter();
    gp_Pnt middle;
    gp_Vec tangent;
    curve.D1(0.5 * (first + last), middle, tangent);
    const double magnitude = tangent.Magnitude();
    if (magnitude > 0.0) {
        tangent /= magnitude;
    }
    const bool is_vertical = std::abs(tangent.Z()) > 1.0 - 1e-7;
    const bool is_horizontal = std::abs(tangent.Z()) < 1e-7;

    double radius = 0.0;
    if (curve_type == GeomAbs_Circle) {
        radius = curve.Circle().Radius();
    }

    const gp_Pnt start = curve.Value(first);
    const gp_Pnt end = curve.Value(last);

    rust::Vec<double> data;
    data.push_back(properties.Mass());
    data.push_back(type_code);
    data.push_back(is_vertical ? 1.0 : 0.0);
    data.push_back(is_horizontal ? 1.0 : 0.0);
    data.push_back(tangent.X());
    data.push_back(tangent.Y());
    data.push_back(tangent.Z());
    data.push_back(radius);
    data.push_back(start.X());
    data.push_back(start.Y());
    data.push_back(start.Z());
    data.push_back(end.X());
    data.push_back(end.Y());
    data.push_back(end.Z());
    return data;
}

}  // namespace

std::size_t solid_edge_count(const Shape& shape, std::size_t solid_index) {
    const TopoDS_Shape solid = nth_solid(shape.topods(), solid_index);
    TopTools_IndexedDataMapOfShapeListOfShape edge_faces;
    TopExp::MapShapesAndAncestors(solid, TopAbs_EDGE, TopAbs_FACE, edge_faces);
    std::size_t count = 0;
    for (TopExp_Explorer explorer(solid, TopAbs_EDGE); explorer.More(); explorer.Next()) {
        if (!is_seam_edge(TopoDS::Edge(explorer.Current()), edge_faces)) {
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
    const TopoDS_Shape solid = nth_solid(shape.topods(), solid_index);
    TopTools_IndexedDataMapOfShapeListOfShape edge_faces;
    TopExp::MapShapesAndAncestors(solid, TopAbs_EDGE, TopAbs_FACE, edge_faces);
    std::size_t current = 0;
    for (TopExp_Explorer explorer(solid, TopAbs_EDGE); explorer.More(); explorer.Next()) {
        const TopoDS_Edge edge = TopoDS::Edge(explorer.Current());
        if (is_seam_edge(edge, edge_faces)) {
            continue;
        }
        if (current == edge_index) {
            return edge_data(edge);
        }
        ++current;
    }
    throw std::runtime_error("edge index out of range");
}

std::unique_ptr<Shape> fillet(
    const Shape& shape,
    std::uint8_t kind,
    rust::Slice<const double> values
) {
    try {
        std::size_t global = 0;
        std::size_t added_total = 0;
        TopoDS_Compound compound;
        BRep_Builder builder;
        builder.MakeCompound(compound);

        for (TopExp_Explorer solids(shape.topods(), TopAbs_SOLID); solids.More();
             solids.Next()) {
            const TopoDS_Shape solid = solids.Current();
            TopTools_IndexedDataMapOfShapeListOfShape edge_faces;
            TopExp::MapShapesAndAncestors(solid, TopAbs_EDGE, TopAbs_FACE, edge_faces);
            std::size_t added = 0;
            TopoDS_Shape result;

            if (kind == 0) {
                BRepFilletAPI_MakeFillet make(solid);
                for (TopExp_Explorer edges(solid, TopAbs_EDGE); edges.More(); edges.Next()) {
                    const TopoDS_Edge edge = TopoDS::Edge(edges.Current());
                    if (is_seam_edge(edge, edge_faces)) {
                        continue;
                    }
                    if (global >= values.size()) {
                        throw std::runtime_error("fillet value array is too short");
                    }
                    const double value = values[global];
                    ++global;
                    if (value > 0.0) {
                        make.Add(value, edge);
                        ++added;
                    }
                }
                if (added > 0) {
                    make.Build();
                    if (!make.IsDone()) {
                        throw std::runtime_error("BRepFilletAPI_MakeFillet did not complete");
                    }
                    result = make.Shape();
                } else {
                    result = solid;
                }
            } else {
                BRepFilletAPI_MakeChamfer make(solid);
                for (TopExp_Explorer edges(solid, TopAbs_EDGE); edges.More(); edges.Next()) {
                    const TopoDS_Edge edge = TopoDS::Edge(edges.Current());
                    if (is_seam_edge(edge, edge_faces)) {
                        continue;
                    }
                    if (global >= values.size()) {
                        throw std::runtime_error("chamfer value array is too short");
                    }
                    const double value = values[global];
                    ++global;
                    if (value > 0.0) {
                        make.Add(value, edge);
                        ++added;
                    }
                }
                if (added > 0) {
                    make.Build();
                    if (!make.IsDone()) {
                        throw std::runtime_error("BRepFilletAPI_MakeChamfer did not complete");
                    }
                    result = make.Shape();
                } else {
                    result = solid;
                }
            }

            builder.Add(compound, result);
            added_total += added;
        }

        if (added_total == 0) {
            return std::make_unique<Shape>(shape.topods());
        }
        ensure_valid(compound);
        return std::make_unique<Shape>(compound);
    } catch (const Standard_Failure& failure) {
        rethrow_as_std_error(failure);
    }
}

bool write_stl(const Shape& shape, rust::Str path, double tolerance) {
    try {
        const TopoDS_Shape& topods = shape.topods();
        const Standard_Real deflection = tolerance > 0.0 ? tolerance : 0.1;
        BRepMesh_IncrementalMesh mesher(topods, deflection, Standard_False, 0.1, Standard_True);
        StlAPI_Writer writer;
        writer.ASCIIMode() = Standard_False;
        const std::string path_string(path.data(), path.size());
        return writer.Write(topods, path_string.c_str());
    } catch (const Standard_Failure& failure) {
        rethrow_as_std_error(failure);
    }
}

}  // namespace vg3
