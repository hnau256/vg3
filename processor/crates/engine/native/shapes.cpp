#include "occt_internal.h"
#include "occt.h"

#include <stdexcept>

#include <StlAPI_Writer.hxx>

#include <Standard_Version.hxx>
#include <TopExp_Explorer.hxx>
#include <TopoDS.hxx>
#include <TopoDS_Compound.hxx>
#include <TopoDS_Face.hxx>
#include <TopoDS_Shape.hxx>

namespace vg3 {

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

rust::String occt_version() {
    return rust::String(OCC_VERSION_COMPLETE);
}

std::unique_ptr<Shape> as_face(const Shape& shape) {
    try {
        TopoDS_Face face;
        Standard_Integer count = 0;
        for (TopExp_Explorer explorer(shape.topods(), TopAbs_FACE); explorer.More();
             explorer.Next()) {
            face = TopoDS::Face(explorer.Current());
            ++count;
        }
        if (count != 1) {
            throw std::runtime_error("a region must be a single planar face");
        }
        return std::make_unique<Shape>(face);
    } catch (const Standard_Failure& failure) {
        detail::rethrow_as_std_error(failure);
    }
}

}  // namespace vg3
