#include "occt_internal.h"
#include "occt.h"

#include <cmath>
#include <cstdint>
#include <stdexcept>
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
                    if (detail::is_seam_edge(edge, edge_faces)) {
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
                    if (detail::is_seam_edge(edge, edge_faces)) {
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
        detail::ensure_valid(compound);
        return std::make_unique<Shape>(compound);
    } catch (const Standard_Failure& failure) {
        detail::rethrow_as_std_error(failure);
    }
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
}  // namespace

std::size_t face_corner_count(const Shape& face) {
    const auto vertex_edges = face_corner_map(face.topods());
    std::size_t count = 0;
    for (Standard_Integer index = 1; index <= vertex_edges.Extent(); ++index) {
        if (vertex_edges.FindFromIndex(index).Extent() >= 2) {
            ++count;
        }
    }
    return count;
}

rust::Vec<double> face_corner_data(const Shape& face, std::size_t corner) {
    const auto vertex_edges = face_corner_map(face.topods());
    std::size_t current = 0;
    for (Standard_Integer index = 1; index <= vertex_edges.Extent(); ++index) {
        const TopTools_ListOfShape& edges = vertex_edges.FindFromIndex(index);
        if (edges.Extent() < 2) {
            continue;
        }
        if (current != corner) {
            ++current;
            continue;
        }
        const TopoDS_Vertex vertex = TopoDS::Vertex(vertex_edges.FindKey(index));
        const gp_Pnt point = BRep_Tool::Pnt(vertex);
        const gp_Vec first = edge_tangent_at(TopoDS::Edge(edges.First()), vertex);
        const gp_Vec second = edge_tangent_at(TopoDS::Edge(edges.Last()), vertex);
        const double angle = first.Angle(second);
        rust::Vec<double> data;
        data.push_back(point.X());
        data.push_back(point.Y());
        data.push_back(point.Z());
        data.push_back(first.X());
        data.push_back(first.Y());
        data.push_back(first.Z());
        data.push_back(second.X());
        data.push_back(second.Y());
        data.push_back(second.Z());
        data.push_back(angle);
        return data;
    }
    throw std::runtime_error("corner index is out of range");
}

std::unique_ptr<Shape> fillet2d(const Shape& profile, rust::Slice<const double> values) {
    try {
        const TopoDS_Face face = TopoDS::Face(profile.topods());
        const auto vertex_edges = face_corner_map(face);

        BRepFilletAPI_MakeFillet2d maker(face);
        std::size_t global = 0;
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
            return std::make_unique<Shape>(face);
        }
        maker.Build();
        if (!maker.IsDone()) {
            throw std::runtime_error("BRepFilletAPI_MakeFillet2d did not complete");
        }
        const TopoDS_Shape result = maker.Shape();
        detail::ensure_valid(result);
        return std::make_unique<Shape>(result);
    } catch (const Standard_Failure& failure) {
        detail::rethrow_as_std_error(failure);
    }
}

}  // namespace vg3
