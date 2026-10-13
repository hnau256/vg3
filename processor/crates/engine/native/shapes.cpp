#include "occt_internal.h"
#include "occt.h"

#include <stdexcept>

#include <StlAPI_Writer.hxx>

#include <Standard_Version.hxx>
#include <BRep_Builder.hxx>
#include <TopExp_Explorer.hxx>
#include <TopoDS.hxx>
#include <TopoDS_Compound.hxx>
#include <TopoDS_Face.hxx>
#include <TopoDS_Shape.hxx>

#include <vector>

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

std::unique_ptr<Shape> as_region(const Shape& shape) {
    return std::make_unique<Shape>(detail::build([&] {
        std::vector<TopoDS_Face> faces;
        for (TopExp_Explorer explorer(shape.topods(), TopAbs_FACE); explorer.More();
             explorer.Next()) {
            faces.push_back(TopoDS::Face(explorer.Current()));
        }
        if (faces.empty()) {
            throw std::runtime_error("a region must contain at least one face");
        }
        if (faces.size() == 1) {
            return TopoDS_Shape(faces.front());
        }
        TopoDS_Compound compound;
        BRep_Builder builder;
        builder.MakeCompound(compound);
        for (const TopoDS_Face& face : faces) {
            builder.Add(compound, face);
        }
        return TopoDS_Shape(compound);
    }));
}

}  // namespace vg3
