#include "occt_internal.h"
#include "occt.h"

#include <stdexcept>

#include <ShapeUpgrade_UnifySameDomain.hxx>
#include <TopExp_Explorer.hxx>
#include <TopAbs.hxx>

namespace vg3 {
bool is_solids_only(const Shape& shape) {
    return detail::solids_only(shape.topods());
}

std::size_t solid_count(const Shape& shape) {
    std::size_t count = 0;
    for (TopExp_Explorer explorer(shape.topods(), TopAbs_SOLID); explorer.More();
         explorer.Next()) {
        ++count;
    }
    return count;
}

std::size_t face_count(const Shape& shape) {
    std::size_t count = 0;
    for (TopExp_Explorer explorer(shape.topods(), TopAbs_FACE); explorer.More();
         explorer.Next()) {
        ++count;
    }
    return count;
}

std::unique_ptr<Shape> unify(const Shape& shape) {
    try {
        ShapeUpgrade_UnifySameDomain algorithm(
            shape.topods(),
            Standard_True,
            Standard_True,
            Standard_True
        );
        algorithm.Build();
        const TopoDS_Shape result = algorithm.Shape();
        if (result.IsNull()) {
            throw std::runtime_error("unify produced a null shape");
        }
        detail::ensure_valid(result);
        return std::make_unique<Shape>(result);
    } catch (const Standard_Failure& failure) {
        detail::rethrow_as_std_error(failure);
    }
}
}  // namespace vg3
