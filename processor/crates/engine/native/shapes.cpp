#include "occt_internal.h"
#include "occt.h"

#include <StlAPI_Writer.hxx>

#include <Standard_Version.hxx>
#include <TopoDS_Compound.hxx>
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

}  // namespace vg3
