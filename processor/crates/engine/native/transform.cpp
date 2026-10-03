#include "occt_internal.h"
#include "occt.h"

#include <stdexcept>

#include <BRepBuilderAPI_GTransform.hxx>
#include <BRepBuilderAPI_Transform.hxx>
#include <BRepOffsetAPI_MakeOffsetShape.hxx>
#include <gp_Ax1.hxx>
#include <gp_Ax2.hxx>
#include <gp_Dir.hxx>
#include <gp_GTrsf.hxx>
#include <gp_Pnt.hxx>
#include <gp_Trsf.hxx>
#include <gp_Vec.hxx>

namespace vg3 {
std::unique_ptr<Shape> translate(const Shape& shape, double x, double y, double z) {
    try {
        gp_Trsf transformation;
        transformation.SetTranslation(gp_Vec(x, y, z));
        BRepBuilderAPI_Transform builder(shape.topods(), transformation, true);
        const TopoDS_Shape result = builder.Shape();
        detail::ensure_valid(result);
        return std::make_unique<Shape>(result);
    } catch (const Standard_Failure& failure) {
        detail::rethrow_as_std_error(failure);
    }
}

std::unique_ptr<Shape> offset(const Shape& shape, double distance) {
    try {
        BRepOffsetAPI_MakeOffsetShape maker;
        maker.PerformByJoin(shape.topods(), distance, detail::kOffsetTolerance);
        if (!maker.IsDone()) {
            throw std::runtime_error("BRepOffsetAPI_MakeOffsetShape did not complete");
        }
        const TopoDS_Shape result = maker.Shape();
        detail::ensure_valid(result);
        return std::make_unique<Shape>(result);
    } catch (const Standard_Failure& failure) {
        detail::rethrow_as_std_error(failure);
    }
}

std::unique_ptr<Shape> rotate(
    const Shape& shape,
    double center_x,
    double center_y,
    double center_z,
    double axis_x,
    double axis_y,
    double axis_z,
    double angle
) {
    try {
        gp_Trsf transformation;
        transformation.SetRotation(
            gp_Ax1(gp_Pnt(center_x, center_y, center_z), gp_Dir(axis_x, axis_y, axis_z)),
            angle
        );
        BRepBuilderAPI_Transform builder(shape.topods(), transformation, true);
        const TopoDS_Shape result = builder.Shape();
        detail::ensure_valid(result);
        return std::make_unique<Shape>(result);
    } catch (const Standard_Failure& failure) {
        detail::rethrow_as_std_error(failure);
    }
}

std::unique_ptr<Shape> mirror(
    const Shape& shape,
    double center_x,
    double center_y,
    double center_z,
    double normal_x,
    double normal_y,
    double normal_z
) {
    try {
        gp_Trsf transformation;
        transformation.SetMirror(
            gp_Ax2(gp_Pnt(center_x, center_y, center_z), gp_Dir(normal_x, normal_y, normal_z))
        );
        BRepBuilderAPI_Transform builder(shape.topods(), transformation, true);
        const TopoDS_Shape result = builder.Shape();
        detail::ensure_valid(result);
        return std::make_unique<Shape>(result);
    } catch (const Standard_Failure& failure) {
        detail::rethrow_as_std_error(failure);
    }
}

std::unique_ptr<Shape> scale(const Shape& shape, double x, double y, double z) {
    try {
        gp_GTrsf transformation;
        transformation.SetValue(1, 1, x);
        transformation.SetValue(2, 2, y);
        transformation.SetValue(3, 3, z);
        BRepBuilderAPI_GTransform builder(shape.topods(), transformation, true);
        const TopoDS_Shape result = builder.Shape();
        detail::ensure_valid(result);
        return std::make_unique<Shape>(result);
    } catch (const Standard_Failure& failure) {
        detail::rethrow_as_std_error(failure);
    }
}

std::unique_ptr<Shape> apply_matrix(const Shape& shape, rust::Slice<const double> matrix) {
    try {
        if (matrix.size() != 16) {
            throw std::runtime_error("matrix must contain exactly 16 elements");
        }
        gp_GTrsf transformation;
        for (int row = 1; row <= 3; ++row) {
            for (int column = 1; column <= 4; ++column) {
                transformation.SetValue(row, column, matrix[(row - 1) * 4 + (column - 1)]);
            }
        }
        BRepBuilderAPI_GTransform builder(shape.topods(), transformation, true);
        const TopoDS_Shape result = builder.Shape();
        detail::ensure_valid(result);
        return std::make_unique<Shape>(result);
    } catch (const Standard_Failure& failure) {
        detail::rethrow_as_std_error(failure);
    }
}
}  // namespace vg3
