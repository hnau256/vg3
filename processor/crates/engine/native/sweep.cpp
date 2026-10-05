#include "occt_internal.h"
#include "occt.h"

#include <stdexcept>

#include <BRepAdaptor_CompCurve.hxx>
#include <BRepBuilderAPI_MakeEdge.hxx>
#include <BRepBuilderAPI_MakeFace.hxx>
#include <BRepBuilderAPI_MakeWire.hxx>
#include <BRepBuilderAPI_Transform.hxx>
#include <BRepLib_MakeFace.hxx>
#include <BRepOffsetAPI_MakePipeShell.hxx>
#include <BRepOffsetAPI_ThruSections.hxx>
#include <BRepTools.hxx>
#include <BRepPrimAPI_MakePrism.hxx>
#include <BRepPrimAPI_MakeRevol.hxx>
#include <TopoDS.hxx>
#include <TopoDS_Face.hxx>
#include <TopoDS_Wire.hxx>
#include <gp_Ax3.hxx>
#include <gp_Circ.hxx>
#include <gp_Dir.hxx>
#include <gp_Pnt.hxx>
#include <gp_Trsf.hxx>
#include <gp_Vec.hxx>

namespace vg3 {

namespace {
TopoDS_Face profile_face(const Shape& profile) {
    const TopoDS_Shape& shape = profile.topods();
    if (shape.ShapeType() == TopAbs_FACE) {
        return TopoDS::Face(shape);
    }
    BRepBuilderAPI_MakeFace make_face(TopoDS::Wire(shape));
    if (!make_face.IsDone()) {
        throw std::runtime_error("profile is not a closed planar contour");
    }
    return make_face.Face();
}
}  // namespace

std::unique_ptr<Shape> make_face(const Shape& wire) {
    try {
        BRepBuilderAPI_MakeFace make_face(TopoDS::Wire(wire.topods()));
        if (!make_face.IsDone()) {
            throw std::runtime_error("cannot build a face from the given contour");
        }
        const TopoDS_Shape face = make_face.Face();
        detail::ensure_valid(face);
        return std::make_unique<Shape>(face);
    } catch (const Standard_Failure& failure) {
        detail::rethrow_as_std_error(failure);
    }
}

std::unique_ptr<Shape> make_circle(double radius) {
    try {
        const gp_Circ circle(gp_Ax2(gp_Pnt(0.0, 0.0, 0.0), gp_Dir(0.0, 0.0, 1.0)), radius);
        BRepBuilderAPI_MakeEdge edge(circle);
        if (!edge.IsDone()) {
            throw std::runtime_error("cannot build a circle edge");
        }
        BRepBuilderAPI_MakeWire wire(edge.Edge());
        if (!wire.IsDone()) {
            throw std::runtime_error("cannot build a circle wire");
        }
        BRepBuilderAPI_MakeFace face(wire.Wire());
        if (!face.IsDone()) {
            throw std::runtime_error("cannot build a circle face");
        }
        const TopoDS_Shape shape = face.Face();
        detail::ensure_valid(shape);
        return std::make_unique<Shape>(shape);
    } catch (const Standard_Failure& failure) {
        detail::rethrow_as_std_error(failure);
    }
}

std::unique_ptr<Shape> extrude(const Shape& profile, double height) {
    try {
        BRepPrimAPI_MakePrism maker(profile_face(profile), gp_Vec(0.0, 0.0, height));
        if (!maker.IsDone()) {
            throw std::runtime_error("BRepPrimAPI_MakePrism did not complete");
        }
        const TopoDS_Shape shape = maker.Shape();
        detail::ensure_valid(shape);
        return std::make_unique<Shape>(shape);
    } catch (const Standard_Failure& failure) {
        detail::rethrow_as_std_error(failure);
    }
}

std::unique_ptr<Shape> revolve(const Shape& profile, double angle) {
    try {
        BRepPrimAPI_MakeRevol maker(
            profile_face(profile),
            gp_Ax1(gp_Pnt(0.0, 0.0, 0.0), gp_Dir(0.0, 1.0, 0.0)),
            angle
        );
        if (!maker.IsDone()) {
            throw std::runtime_error("BRepPrimAPI_MakeRevol did not complete");
        }
        const TopoDS_Shape shape = maker.Shape();
        detail::ensure_valid(shape);
        return std::make_unique<Shape>(shape);
    } catch (const Standard_Failure& failure) {
        detail::rethrow_as_std_error(failure);
    }
}

std::unique_ptr<Shape> sweep(const Shape& profile, const Shape& spine, bool follow) {
    try {
        const TopoDS_Wire spine_wire = TopoDS::Wire(spine.topods());

        // A sketch is a planar face; the pipe shell is built from its outer wire (holes are not
        // part of a swept section — the same limitation as a bare contour profile).
        const TopoDS_Shape& section = profile.topods();
        const TopoDS_Shape section_wire = section.ShapeType() == TopAbs_FACE
            ? BRepTools::OuterWire(TopoDS::Face(section))
            : section;

        // The profile lives in the XY plane; place it at the spine start, its plane perpendicular
        // to the tangent. Section frame (documented in FORMAT.md): local X is radial — away from
        // the Z axis — and local Y runs along +Z, so a profile swept along a helix about +Z
        // becomes a thread ridge rather than a thin fin.
        BRepAdaptor_CompCurve start_curve(spine_wire);
        gp_Pnt start_point;
        gp_Vec start_tangent;
        start_curve.D1(start_curve.FirstParameter(), start_point, start_tangent);
        if (start_tangent.Magnitude() <= gp::Resolution()) {
            throw std::runtime_error("sweep spine has a zero tangent at its start");
        }
        const gp_Dir tangent(start_tangent);

        const auto perpendicular_to_tangent = [&start_tangent](gp_Vec candidate) {
            return candidate - start_tangent * (candidate.Dot(start_tangent)
                                                / start_tangent.Dot(start_tangent));
        };
        gp_Vec radial = perpendicular_to_tangent(gp_Vec(start_point.X(), start_point.Y(), 0.0));
        for (const gp_Vec& fallback :
             {gp_Vec(0.0, 0.0, 1.0), gp_Vec(1.0, 0.0, 0.0), gp_Vec(0.0, 1.0, 0.0)}) {
            if (radial.Magnitude() > gp::Resolution()) {
                break;
            }
            radial = perpendicular_to_tangent(fallback);
        }
        if (radial.Magnitude() <= gp::Resolution()) {
            throw std::runtime_error("cannot orient the sweep section at the spine start");
        }

        gp_Trsf placement;
        placement.SetDisplacement(
            gp_Ax3(gp_Pnt(0.0, 0.0, 0.0), gp_Dir(0.0, 0.0, 1.0)),
            gp_Ax3(start_point, tangent.Reversed(), gp_Dir(radial))
        );
        BRepBuilderAPI_Transform transform(section_wire, placement, true);
        const TopoDS_Shape placed_profile = transform.Shape();

        BRepOffsetAPI_MakePipeShell pipe(spine_wire);
        if (follow) {
            pipe.SetMode(Standard_True);
        } else {
            pipe.SetMode(gp_Ax2(start_point, tangent));
        }
        pipe.SetTransitionMode(BRepBuilderAPI_RightCorner);
        pipe.Add(placed_profile, Standard_False, Standard_False);
        pipe.Build();
        if (!pipe.IsDone()) {
            throw std::runtime_error("BRepOffsetAPI_MakePipeShell did not complete");
        }
        pipe.MakeSolid();
        const TopoDS_Shape shape = pipe.Shape();
        detail::ensure_valid(shape);
        return std::make_unique<Shape>(shape);
    } catch (const Standard_Failure& failure) {
        detail::rethrow_as_std_error(failure);
    }
}

LoftBuilder::LoftBuilder(bool ruled) : thru_(Standard_True, ruled) {}

void LoftBuilder::add(const Shape& section) {
    thru_.AddWire(TopoDS::Wire(section.topods()));
}

std::unique_ptr<Shape> LoftBuilder::finish() {
    try {
        thru_.Build();
        if (!thru_.IsDone()) {
            throw std::runtime_error("BRepOffsetAPI_ThruSections did not complete");
        }
        const TopoDS_Shape shape = thru_.Shape();
        detail::ensure_valid(shape);
        return std::make_unique<Shape>(shape);
    } catch (const Standard_Failure& failure) {
        detail::rethrow_as_std_error(failure);
    }
}

std::unique_ptr<LoftBuilder> new_loft_builder(bool ruled) {
    return std::make_unique<LoftBuilder>(ruled);
}
}  // namespace vg3
