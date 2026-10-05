#include "occt_internal.h"
#include "occt.h"

#include <cstdint>
#include <stdexcept>

#include <BRepFilletAPI_MakeChamfer.hxx>
#include <BRepFilletAPI_MakeFillet.hxx>
#include <BRepFilletAPI_MakeFillet2d.hxx>
#include <BRep_Builder.hxx>
#include <TopExp.hxx>
#include <TopExp_Explorer.hxx>
#include <TopTools_IndexedDataMapOfShapeListOfShape.hxx>
#include <TopoDS.hxx>
#include <TopoDS_Compound.hxx>
#include <TopoDS_Face.hxx>
#include <TopoDS_Vertex.hxx>

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

std::unique_ptr<Shape> fillet2d(const Shape& profile, double radius) {
    try {
        const TopoDS_Face face = TopoDS::Face(profile.topods());

        // Fillet every corner (a vertex shared by at least two edges); a seam vertex of a closed
        // edge (e.g. a circle) has a single edge and is skipped.
        TopTools_IndexedDataMapOfShapeListOfShape vertex_edges;
        TopExp::MapShapesAndAncestors(face, TopAbs_VERTEX, TopAbs_EDGE, vertex_edges);

        BRepFilletAPI_MakeFillet2d maker(face);
        std::size_t added = 0;
        for (Standard_Integer index = 1; index <= vertex_edges.Extent(); ++index) {
            if (vertex_edges.FindFromIndex(index).Extent() < 2) {
                continue;
            }
            maker.AddFillet(TopoDS::Vertex(vertex_edges.FindKey(index)), radius);
            ++added;
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
