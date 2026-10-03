#include "occt_internal.h"
#include "occt.h"

#include <stdexcept>

#include <BRepAlgoAPI_Common.hxx>
#include <BRepAlgoAPI_Cut.hxx>
#include <BRepAlgoAPI_Fuse.hxx>
#include <TopTools_ListOfShape.hxx>

namespace vg3 {

namespace {
template <typename Operation>
TopoDS_Shape run_boolean(
    const TopTools_ListOfShape& arguments,
    const TopTools_ListOfShape& tools
) {
    Operation operation;
    operation.SetArguments(arguments);
    operation.SetTools(tools);
    operation.SetRunParallel(Standard_False);
    operation.Build();
    if (!operation.IsDone()) {
        throw std::runtime_error("boolean operation did not complete");
    }
    return operation.Shape();
}

}  // namespace
BooleanBuilder::BooleanBuilder(std::uint8_t kind) : kind_(kind) {}

void BooleanBuilder::add_argument(const Shape& shape) {
    arguments_.Append(shape.topods());
}

void BooleanBuilder::add_tool(const Shape& shape) {
    tools_.Append(shape.topods());
}

std::unique_ptr<Shape> BooleanBuilder::finish() {
    try {
        TopoDS_Shape result;
        switch (kind_) {
            case 0:
                result = run_boolean<BRepAlgoAPI_Fuse>(arguments_, tools_);
                break;
            case 1:
                result = run_boolean<BRepAlgoAPI_Cut>(arguments_, tools_);
                break;
            case 2:
                result = run_boolean<BRepAlgoAPI_Common>(arguments_, tools_);
                break;
            default:
                throw std::runtime_error("unknown boolean kind");
        }
        detail::ensure_valid(result);
        return std::make_unique<Shape>(result);
    } catch (const Standard_Failure& failure) {
        detail::rethrow_as_std_error(failure);
    }
}

std::unique_ptr<BooleanBuilder> new_boolean_builder(std::uint8_t kind) {
    return std::make_unique<BooleanBuilder>(kind);
}
}  // namespace vg3
