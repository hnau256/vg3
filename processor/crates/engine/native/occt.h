#pragma once

#include <cstddef>
#include <cstdint>
#include <memory>
#include <vector>

#include <rust/cxx.h>

#include <BRepBuilderAPI_MakeWire.hxx>
#include <BRepOffsetAPI_ThruSections.hxx>
#include <BRep_Builder.hxx>
#include <TopoDS_Compound.hxx>
#include <TopoDS_Edge.hxx>
#include <TopoDS_Shape.hxx>
#include <TopoDS_Vertex.hxx>
#include <gp_Pnt.hxx>

#include <TDocStd_Document.hxx>
#include <XCAFDoc_ShapeTool.hxx>
#include <XCAFDoc_ColorTool.hxx>

namespace vg3 {

class Shape {
public:
    explicit Shape(const TopoDS_Shape& shape);

    const TopoDS_Shape& topods() const;

private:
    TopoDS_Shape shape_;
};

class CompoundBuilder {
public:
    CompoundBuilder();

    void push(const Shape& shape);

    std::unique_ptr<Shape> finish();

private:
    TopoDS_Compound compound_;
    BRep_Builder builder_;
};

class WireBuilder {
public:
    WireBuilder();

    void start(double x, double y, double z);

    void line(double x, double y, double z);

    void arc(
        double via_x,
        double via_y,
        double via_z,
        double to_x,
        double to_y,
        double to_z
    );

    void spline(rust::Slice<const double> points);

    void helix(double pitch, double height, bool right_handed);

    std::unique_ptr<Shape> finish(bool closed);

private:
    TopoDS_Vertex vertex_for(const gp_Pnt& point);

    void add_edge(const TopoDS_Edge& edge);

    BRepBuilderAPI_MakeWire wire_;
    gp_Pnt start_point_;
    gp_Pnt current_point_;
    TopoDS_Vertex start_vertex_;
    TopoDS_Vertex current_vertex_;
    bool started_;
    bool has_edges_;
};

std::unique_ptr<CompoundBuilder> new_compound_builder();

std::unique_ptr<WireBuilder> new_wire_builder();

rust::String occt_version();

std::unique_ptr<Shape> make_box(double width, double length, double height);

std::unique_ptr<Shape> make_sphere(double radius, double angle);

std::unique_ptr<Shape> make_cylinder(double radius, double height, double angle);

std::unique_ptr<Shape> make_cone(
    double radius_bottom,
    double radius_top,
    double height,
    double angle
);

std::unique_ptr<Shape> make_torus(double major_radius, double minor_radius, double angle);

std::unique_ptr<Shape> make_wedge(double width, double length, double height, double top_width);

std::unique_ptr<Shape> make_halfspace();

std::unique_ptr<Shape> make_polyhedron(
    rust::Slice<const double> points,
    rust::Slice<const std::uint32_t> face_indices,
    rust::Slice<const std::uint32_t> face_offsets
);

std::unique_ptr<Shape> make_face(const Shape& wire);

std::unique_ptr<Shape> make_circle(double radius);

/// Reduces a shape (a face, or a compound such as a boolean result) to its single planar face.
std::unique_ptr<Shape> as_face(const Shape& shape);

std::unique_ptr<Shape> extrude(const Shape& profile, double height);

std::unique_ptr<Shape> revolve(const Shape& profile, double angle);

std::unique_ptr<Shape> sweep(
    const Shape& profile,
    const Shape& spine,
    bool follow,
    std::uint8_t transition
);

class LoftBuilder {
public:
    explicit LoftBuilder(
        bool ruled,
        bool smoothing,
        bool has_continuity,
        std::uint8_t continuity,
        bool has_parametrization,
        std::uint8_t parametrization,
        std::int32_t max_degree,
        bool check_compatibility
    );

    void add(const Shape& section);

    std::unique_ptr<Shape> finish();

private:
    BRepOffsetAPI_ThruSections thru_;
};

std::unique_ptr<LoftBuilder> new_loft_builder(
    bool ruled,
    bool smoothing,
    bool has_continuity,
    std::uint8_t continuity,
    bool has_parametrization,
    std::uint8_t parametrization,
    std::int32_t max_degree,
    bool check_compatibility
);

rust::Vec<double> triangulation(const Shape& shape, double tolerance);

rust::Vec<double> bounding_box(const Shape& shape);

double volume(const Shape& shape);

std::size_t solid_edge_count(const Shape& shape, std::size_t solid_index);

rust::Vec<double> solid_edge_data(
    const Shape& shape,
    std::size_t solid_index,
    std::size_t edge_index
);

std::unique_ptr<Shape> fillet(
    const Shape& shape,
    std::uint8_t kind,
    rust::Slice<const double> values
);

std::unique_ptr<Shape> fillet2d(const Shape& profile, rust::Slice<const double> values);

std::size_t face_corner_count(const Shape& face);

rust::Vec<double> face_corner_data(const Shape& face, std::size_t corner);

class BooleanBuilder {
public:
    explicit BooleanBuilder(std::uint8_t kind);

    void add_argument(const Shape& shape);

    void add_tool(const Shape& shape);

    std::unique_ptr<Shape> finish();

private:
    std::uint8_t kind_;
    TopTools_ListOfShape arguments_;
    TopTools_ListOfShape tools_;
};

std::unique_ptr<BooleanBuilder> new_boolean_builder(std::uint8_t kind);

std::unique_ptr<Shape> translate(const Shape& shape, double x, double y, double z);

std::unique_ptr<Shape> offset(const Shape& shape, double distance, std::uint8_t join);

std::unique_ptr<Shape> offset2d(const Shape& profile, double distance, std::uint8_t join);

std::unique_ptr<Shape> thick_solid(
    const Shape& shape,
    rust::Slice<const std::uint32_t> faces,
    double offset,
    std::uint8_t join
);

std::unique_ptr<Shape> rotate(
    const Shape& shape,
    double center_x,
    double center_y,
    double center_z,
    double axis_x,
    double axis_y,
    double axis_z,
    double angle
);

std::unique_ptr<Shape> mirror(
    const Shape& shape,
    double center_x,
    double center_y,
    double center_z,
    double normal_x,
    double normal_y,
    double normal_z
);

std::unique_ptr<Shape> scale(const Shape& shape, double x, double y, double z);

std::unique_ptr<Shape> apply_matrix(const Shape& shape, rust::Slice<const double> matrix);

bool is_solids_only(const Shape& shape);

std::size_t solid_count(const Shape& shape);

std::size_t face_count(const Shape& shape);

rust::Vec<double> face_data(const Shape& shape, std::size_t index);

std::unique_ptr<Shape> unify(const Shape& shape);

bool write_stl(const Shape& shape, rust::Str path, double tolerance);

class StepBuilder {
public:
    StepBuilder();

    void add_part(
        const Shape& shape,
        rust::Str name,
        bool has_color,
        double r,
        double g,
        double b
    );

    bool write(rust::Str path);

private:
    Handle(TDocStd_Document) document_;
    Handle(XCAFDoc_ShapeTool) shapes_;
    Handle(XCAFDoc_ColorTool) colors_;
};

std::unique_ptr<StepBuilder> new_step_builder();

rust::Vec<std::uint8_t> brep_encode(const Shape& shape);

std::unique_ptr<Shape> brep_decode(rust::Slice<const std::uint8_t> bytes);

}  // namespace vg3
