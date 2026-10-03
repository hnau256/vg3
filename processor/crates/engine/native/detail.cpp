#include "occt_internal.h"

#include <stdexcept>
#include <string>

#include <BRepCheck_Analyzer.hxx>
#include <BRep_Tool.hxx>
#include <TopAbs.hxx>
#include <TopExp_Explorer.hxx>
#include <TopTools_ListIteratorOfListOfShape.hxx>
#include <TopTools_ListOfShape.hxx>
#include <TopoDS.hxx>
#include <TopoDS_Face.hxx>
#include <TopoDS_Iterator.hxx>

namespace vg3::detail {

void rethrow_as_std_error(const Standard_Failure& failure) {
    throw std::runtime_error(failure.GetMessageString());
}

void ensure_valid(const TopoDS_Shape& shape) {
    BRepCheck_Analyzer analyzer(shape);
    if (!analyzer.IsValid()) {
        throw std::runtime_error("OpenCASCADE produced an invalid shape");
    }
}

bool solids_only(const TopoDS_Shape& shape) {
    switch (shape.ShapeType()) {
        case TopAbs_SOLID:
            return true;
        case TopAbs_COMPOUND:
        case TopAbs_COMPSOLID: {
            for (TopoDS_Iterator iterator(shape); iterator.More(); iterator.Next()) {
                if (!solids_only(iterator.Value())) {
                    return false;
                }
            }
            return true;
        }
        default:
            return false;
    }
}

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

}  // namespace vg3::detail
