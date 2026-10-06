#include "occt_internal.h"
#include "occt.h"

#include <stdexcept>

#include <BRepBuilderAPI_GTransform.hxx>
#include <BRepBuilderAPI_MakeFace.hxx>
#include <BRepBuilderAPI_Transform.hxx>
#include <BRepOffsetAPI_MakeOffset.hxx>
#include <BRepOffsetAPI_MakeOffsetShape.hxx>
#include <BRepOffsetAPI_MakeThickSolid.hxx>
#include <BRepOffset_Mode.hxx>
#include <BRep_Tool.hxx>
#include <GeomAbs_JoinType.hxx>
#include <TopExp_Explorer.hxx>
#include <TopTools_ListOfShape.hxx>
#include <TopoDS.hxx>
#include <TopoDS_Face.hxx>
#include <TopoDS_Wire.hxx>
#include <gp_Ax1.hxx>
#include <gp_Ax2.hxx>
#include <gp_Dir.hxx>
#include <gp_GTrsf.hxx>
#include <gp_Pnt.hxx>
#include <gp_Trsf.hxx>
#include <gp_Vec.hxx>
#include <set>

namespace vg3 {
std::unique_ptr<Shape> translate(const Shape& shape, double x, double y, double z) {
    return std::make_unique<Shape>(detail::build([&] {
        gp_Trsf transformation;
        transformation.SetTranslation(gp_Vec(x, y, z));
        BRepBuilderAPI_Transform builder(shape.topods(), transformation, true);
        return builder.Shape();
    }));
}

std::unique_ptr<Shape> offset(const Shape& shape, double distance, std::uint8_t join) {
    return std::make_unique<Shape>(detail::build([&] {
        BRepOffsetAPI_MakeOffsetShape maker;
        maker.PerformByJoin(
            shape.topods(),
            distance,
            detail::kOffsetTolerance,
            BRepOffset_Skin,
            Standard_False,
            Standard_False,
            detail::join_type(join),
            Standard_False
        );
        if (!maker.IsDone()) {
            throw std::runtime_error("BRepOffsetAPI_MakeOffsetShape did not complete");
        }
        return maker.Shape();
    }));
}

std::unique_ptr<Shape> offset2d(const Shape& profile, double distance, std::uint8_t join) {
    return std::make_unique<Shape>(detail::build([&] {
        const TopoDS_Face face = TopoDS::Face(profile.topods());

        // Offset the whole face (not just the outer wire): holes are offset in the right direction
        // and preserved. The result is a compound of wires; rebuild the face from all of them —
        // OCCT classifies the outer boundary and the holes itself.
        BRepOffsetAPI_MakeOffset maker(face, detail::join_type(join), Standard_False);
        maker.Perform(distance);
        if (!maker.IsDone()) {
            throw std::runtime_error("BRepOffsetAPI_MakeOffset did not complete");
        }
        BRepBuilderAPI_MakeFace make_face(BRep_Tool::Surface(face), detail::kPointTolerance);
        for (TopExp_Explorer explorer(maker.Shape(), TopAbs_WIRE); explorer.More();
             explorer.Next()) {
            make_face.Add(TopoDS::Wire(explorer.Current()));
        }
        if (!make_face.IsDone()) {
            throw std::runtime_error("cannot build a face from the offset contour");
        }
        return make_face.Face();
    }));
}

std::unique_ptr<Shape> thick_solid(
    const Shape& shape,
    rust::Slice<const std::uint32_t> faces,
    double offset,
    std::uint8_t join
) {
    return std::make_unique<Shape>(detail::build([&] {
        const std::set<std::size_t> selected(faces.begin(), faces.end());
        TopTools_ListOfShape closing;
        std::size_t index = 0;
        for (TopExp_Explorer explorer(shape.topods(), TopAbs_FACE); explorer.More();
             explorer.Next()) {
            if (selected.count(index) != 0) {
                closing.Append(explorer.Current());
            }
            ++index;
        }

        BRepOffsetAPI_MakeThickSolid maker;
        maker.MakeThickSolidByJoin(
            shape.topods(),
            closing,
            offset,
            detail::kOffsetTolerance,
            BRepOffset_Skin,
            Standard_False,
            Standard_False,
            detail::join_type(join),
            Standard_False
        );
        if (!maker.IsDone()) {
            throw std::runtime_error("BRepOffsetAPI_MakeThickSolid did not complete");
        }
        return maker.Shape();
    }));
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
    return std::make_unique<Shape>(detail::build([&] {
        gp_Trsf transformation;
        transformation.SetRotation(
            gp_Ax1(gp_Pnt(center_x, center_y, center_z), gp_Dir(axis_x, axis_y, axis_z)),
            angle
        );
        BRepBuilderAPI_Transform builder(shape.topods(), transformation, true);
        return builder.Shape();
    }));
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
    return std::make_unique<Shape>(detail::build([&] {
        gp_Trsf transformation;
        transformation.SetMirror(
            gp_Ax2(gp_Pnt(center_x, center_y, center_z), gp_Dir(normal_x, normal_y, normal_z))
        );
        BRepBuilderAPI_Transform builder(shape.topods(), transformation, true);
        return builder.Shape();
    }));
}

std::unique_ptr<Shape> scale(const Shape& shape, double x, double y, double z) {
    return std::make_unique<Shape>(detail::build([&] {
        gp_GTrsf transformation;
        transformation.SetValue(1, 1, x);
        transformation.SetValue(2, 2, y);
        transformation.SetValue(3, 3, z);
        BRepBuilderAPI_GTransform builder(shape.topods(), transformation, true);
        return builder.Shape();
    }));
}

std::unique_ptr<Shape> apply_matrix(const Shape& shape, rust::Slice<const double> matrix) {
    return std::make_unique<Shape>(detail::build([&] {
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
        return builder.Shape();
    }));
}
}  // namespace vg3
