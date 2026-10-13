#include "occt_internal.h"
#include "occt.h"

#include <cmath>
#include <cstdint>
#include <stdexcept>
#include <string>
#include <vector>

#include <BRepAdaptor_Curve.hxx>
#include <BRepFilletAPI_MakeChamfer.hxx>
#include <BRepFilletAPI_MakeFillet.hxx>
#include <BRepFilletAPI_MakeFillet2d.hxx>
#include <BRep_Builder.hxx>
#include <BRep_Tool.hxx>
#include <TopExp.hxx>
#include <TopExp_Explorer.hxx>
#include <TopTools_IndexedDataMapOfShapeListOfShape.hxx>
#include <TopoDS.hxx>
#include <TopoDS_Compound.hxx>
#include <TopoDS_Edge.hxx>
#include <TopoDS_Face.hxx>
#include <TopoDS_Vertex.hxx>
#include <gp_Pnt.hxx>
#include <gp_Vec.hxx>

namespace vg3 {

namespace {
/// Adds a value to a fresh maker for every non-seam edge of `solid`, skipping non-positive values,
/// and builds the result (or returns `solid` unchanged when nothing was added).
template <typename Maker>
TopoDS_Shape fillet_solid(
    const TopoDS_Shape& solid,
    const TopTools_IndexedDataMapOfShapeListOfShape& edge_faces,
    rust::Slice<const double> values,
    std::size_t& global,
    std::size_t& added_total,
    const char* maker_name,
    const char* value_name
) {
    Maker make(solid);
    std::size_t added = 0;
    for (TopExp_Explorer edges(solid, TopAbs_EDGE); edges.More(); edges.Next()) {
        const TopoDS_Edge edge = TopoDS::Edge(edges.Current());
        if (detail::is_seam_edge(edge, edge_faces)) {
            continue;
        }
        if (global >= values.size()) {
            throw std::runtime_error(std::string(value_name) + " value array is too short");
        }
        const double value = values[global];
        ++global;
        if (value > 0.0) {
            make.Add(value, edge);
            ++added;
        }
    }
    added_total += added;
    if (added == 0) {
        return solid;
    }
    make.Build();
    if (!make.IsDone()) {
        throw std::runtime_error(std::string(maker_name) + " did not complete");
    }
    return make.Shape();
}
}  // namespace

std::unique_ptr<Shape> fillet(
    const Shape& shape,
    std::uint8_t kind,
    rust::Slice<const double> values
) {
    return std::make_unique<Shape>(detail::build([&] {
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
            const TopoDS_Shape result = kind == 0
                ? fillet_solid<BRepFilletAPI_MakeFillet>(
                      solid, edge_faces, values, global, added_total,
                      "BRepFilletAPI_MakeFillet", "fillet")
                : fillet_solid<BRepFilletAPI_MakeChamfer>(
                      solid, edge_faces, values, global, added_total,
                      "BRepFilletAPI_MakeChamfer", "chamfer");
            builder.Add(compound, result);
        }

        if (added_total == 0) {
            return shape.topods();
        }
        return TopoDS_Shape(compound);
    }));
}

namespace {
/// Vertices of `face` shared by at least two edges, in a deterministic order (a seam vertex of a
/// closed edge, e.g. a circle, has a single edge and is not a corner).
TopTools_IndexedDataMapOfShapeListOfShape face_corner_map(const TopoDS_Shape& face) {
    TopTools_IndexedDataMapOfShapeListOfShape vertex_edges;
    TopExp::MapShapesAndAncestors(face, TopAbs_VERTEX, TopAbs_EDGE, vertex_edges);
    return vertex_edges;
}

/// The unit tangent of `edge` at `vertex`, oriented away from the vertex.
gp_Vec edge_tangent_at(const TopoDS_Edge& edge, const TopoDS_Vertex& vertex) {
    BRepAdaptor_Curve curve(edge);
    const double parameter = BRep_Tool::Parameter(vertex, edge);
    gp_Pnt point;
    gp_Vec tangent;
    curve.D1(parameter, point, tangent);
    const double first = curve.FirstParameter();
    const double last = curve.LastParameter();
    if (std::abs(parameter - last) < std::abs(parameter - first)) {
        tangent.Reverse();
    }
    return tangent.Normalized();
}

/// Calls `visit` for every corner (a vertex shared by at least two edges) of the planar region, in
/// explorer order: face by face, and within a face in `face_corner_map` order. The region may
/// consist of several disjoint faces, so corners are enumerated per face and indexed globally.
template <typename F>
void for_each_corner(const TopoDS_Shape& region, F&& visit) {
    for (TopExp_Explorer faces(region, TopAbs_FACE); faces.More(); faces.Next()) {
        const TopoDS_Face face = TopoDS::Face(faces.Current());
        const auto vertex_edges = face_corner_map(face);
        for (Standard_Integer index = 1; index <= vertex_edges.Extent(); ++index) {
            const TopTools_ListOfShape& edges = vertex_edges.FindFromIndex(index);
            if (edges.Extent() < 2) {
                continue;
            }
            visit(TopoDS::Vertex(vertex_edges.FindKey(index)), edges);
        }
    }
}
}  // namespace

std::size_t region_corner_count(const Shape& region) {
    std::size_t count = 0;
    for_each_corner(
        region.topods(),
        [&](const TopoDS_Vertex&, const TopTools_ListOfShape&) { ++count; }
    );
    return count;
}

rust::Vec<double> region_corner_data(const Shape& region, std::size_t corner) {
    rust::Vec<double> result;
    std::size_t current = 0;
    bool found = false;
    for_each_corner(
        region.topods(),
        [&](const TopoDS_Vertex& vertex, const TopTools_ListOfShape& edges) {
            if (current++ != corner) {
                return;
            }
            const gp_Pnt point = BRep_Tool::Pnt(vertex);
            const gp_Vec first = edge_tangent_at(TopoDS::Edge(edges.First()), vertex);
            const gp_Vec second = edge_tangent_at(TopoDS::Edge(edges.Last()), vertex);
            const double angle = first.Angle(second);
            result.push_back(point.X());
            result.push_back(point.Y());
            result.push_back(point.Z());
            result.push_back(first.X());
            result.push_back(first.Y());
            result.push_back(first.Z());
            result.push_back(second.X());
            result.push_back(second.Y());
            result.push_back(second.Z());
            result.push_back(angle);
            found = true;
        }
    );
    if (!found) {
        throw std::runtime_error("corner index is out of range");
    }
    return result;
}

std::unique_ptr<Shape> fillet2d(const Shape& profile, rust::Slice<const double> values) {
    return std::make_unique<Shape>(detail::build([&] {
        std::size_t global = 0;
        return detail::for_each_face(
            profile.topods(),
            [&](const TopoDS_Face& face) -> TopoDS_Shape {
                const auto vertex_edges = face_corner_map(face);
                BRepFilletAPI_MakeFillet2d maker(face);
                std::size_t added = 0;
                for (Standard_Integer index = 1; index <= vertex_edges.Extent(); ++index) {
                    if (vertex_edges.FindFromIndex(index).Extent() < 2) {
                        continue;
                    }
                    if (global >= values.size()) {
                        throw std::runtime_error("fillet2d value array is too short");
                    }
                    const double value = values[global];
                    ++global;
                    if (value > 0.0) {
                        maker.AddFillet(TopoDS::Vertex(vertex_edges.FindKey(index)), value);
                        ++added;
                    }
                }
                if (added == 0) {
                    return TopoDS_Shape(face);
                }
                maker.Build();
                if (!maker.IsDone()) {
                    throw std::runtime_error("BRepFilletAPI_MakeFillet2d did not complete");
                }
                return maker.Shape();
            }
        );
    }));
}

}  // namespace vg3
