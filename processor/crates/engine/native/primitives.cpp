#include "occt_internal.h"
#include "occt.h"

#include <stdexcept>
#include <vector>

#include <BRepBuilderAPI_MakeFace.hxx>
#include <BRepBuilderAPI_MakePolygon.hxx>
#include <BRepBuilderAPI_MakeSolid.hxx>
#include <BRepBuilderAPI_Sewing.hxx>
#include <BRepLib_MakeFace.hxx>
#include <BRepPrimAPI_MakeHalfSpace.hxx>
#include <BRepPrimAPI_MakeBox.hxx>
#include <BRepPrimAPI_MakeCone.hxx>
#include <BRepPrimAPI_MakeCylinder.hxx>
#include <BRepPrimAPI_MakeSphere.hxx>
#include <BRepPrimAPI_MakeTorus.hxx>
#include <BRepPrimAPI_MakeWedge.hxx>
#include <TopExp_Explorer.hxx>
#include <TopoDS.hxx>
#include <TopoDS_Shell.hxx>
#include <gp_Pln.hxx>
#include <gp_Pnt.hxx>

namespace vg3 {
std::unique_ptr<Shape> make_box(double width, double length, double height) {
    try {
        BRepPrimAPI_MakeBox maker(width, length, height);
        const TopoDS_Shape shape = maker.Shape();
        detail::ensure_valid(shape);
        return std::make_unique<Shape>(shape);
    } catch (const Standard_Failure& failure) {
        detail::rethrow_as_std_error(failure);
    }
}

std::unique_ptr<Shape> make_sphere(double radius) {
    try {
        BRepPrimAPI_MakeSphere maker(radius);
        const TopoDS_Shape shape = maker.Shape();
        detail::ensure_valid(shape);
        return std::make_unique<Shape>(shape);
    } catch (const Standard_Failure& failure) {
        detail::rethrow_as_std_error(failure);
    }
}

std::unique_ptr<Shape> make_cylinder(double radius, double height) {
    try {
        BRepPrimAPI_MakeCylinder maker(radius, height);
        const TopoDS_Shape shape = maker.Shape();
        detail::ensure_valid(shape);
        return std::make_unique<Shape>(shape);
    } catch (const Standard_Failure& failure) {
        detail::rethrow_as_std_error(failure);
    }
}

std::unique_ptr<Shape> make_cone(double radius_bottom, double radius_top, double height) {
    try {
        BRepPrimAPI_MakeCone maker(radius_bottom, radius_top, height);
        const TopoDS_Shape shape = maker.Shape();
        detail::ensure_valid(shape);
        return std::make_unique<Shape>(shape);
    } catch (const Standard_Failure& failure) {
        detail::rethrow_as_std_error(failure);
    }
}

std::unique_ptr<Shape> make_torus(double major_radius, double minor_radius) {
    try {
        BRepPrimAPI_MakeTorus maker(major_radius, minor_radius);
        const TopoDS_Shape shape = maker.Shape();
        detail::ensure_valid(shape);
        return std::make_unique<Shape>(shape);
    } catch (const Standard_Failure& failure) {
        detail::rethrow_as_std_error(failure);
    }
}

std::unique_ptr<Shape> make_halfspace() {
    try {
        const gp_Pln plane;
        BRepLib_MakeFace make_face(plane);
        BRepPrimAPI_MakeHalfSpace make_half_space(make_face.Face(), gp_Pnt(0.0, 0.0, -1.0));
        const TopoDS_Shape shape = make_half_space.Solid();
        detail::ensure_valid(shape);
        return std::make_unique<Shape>(shape);
    } catch (const Standard_Failure& failure) {
        detail::rethrow_as_std_error(failure);
    }
}

std::unique_ptr<Shape> make_polyhedron(
    rust::Slice<const double> points,
    rust::Slice<const std::uint32_t> face_indices,
    rust::Slice<const std::uint32_t> face_offsets
) {
    try {
        if (points.size() % 3 != 0) {
            throw std::runtime_error("polyhedron points must be xyz triples");
        }
        const std::size_t point_count = points.size() / 3;
        if (point_count < 3 || face_offsets.size() < 2) {
            throw std::runtime_error("polyhedron needs at least three points and one face");
        }
        std::vector<gp_Pnt> vertices;
        vertices.reserve(point_count);
        for (std::size_t i = 0; i < point_count; ++i) {
            vertices.emplace_back(points[i * 3], points[i * 3 + 1], points[i * 3 + 2]);
        }

        const std::size_t face_count = face_offsets.size() - 1;
        BRep_Builder builder;
        TopoDS_Compound compound;
        builder.MakeCompound(compound);
        for (std::size_t face = 0; face < face_count; ++face) {
            const std::uint32_t begin = face_offsets[face];
            const std::uint32_t end = face_offsets[face + 1];
            if (end - begin < 3) {
                throw std::runtime_error("polyhedron face needs at least three vertices");
            }
            BRepBuilderAPI_MakePolygon polygon;
            for (std::uint32_t i = begin; i < end; ++i) {
                const std::uint32_t index = face_indices[i];
                if (index >= point_count) {
                    throw std::runtime_error("polyhedron face references a missing point");
                }
                polygon.Add(vertices[index]);
            }
            polygon.Close();
            if (!polygon.IsDone()) {
                throw std::runtime_error("cannot build a polyhedron face wire");
            }
            BRepBuilderAPI_MakeFace face_maker(polygon.Wire());
            if (!face_maker.IsDone()) {
                throw std::runtime_error("polyhedron face is not planar");
            }
            builder.Add(compound, face_maker.Face());
        }

        BRepBuilderAPI_Sewing sewing(1.0e-6);
        sewing.Add(compound);
        sewing.Perform();
        const TopoDS_Shape sewed = sewing.SewedShape();

        TopoDS_Shell shell;
        bool has_shell = false;
        for (TopExp_Explorer explorer(sewed, TopAbs_SHELL); explorer.More(); explorer.Next()) {
            shell = TopoDS::Shell(explorer.Current());
            has_shell = true;
            break;
        }
        if (!has_shell) {
            throw std::runtime_error("polyhedron faces did not form a closed shell");
        }
        BRepBuilderAPI_MakeSolid make_solid(shell);
        if (!make_solid.IsDone()) {
            throw std::runtime_error("polyhedron shell is not a closed solid");
        }
        const TopoDS_Shape shape = make_solid.Solid();
        detail::ensure_valid(shape);
        return std::make_unique<Shape>(shape);
    } catch (const Standard_Failure& failure) {
        detail::rethrow_as_std_error(failure);
    }
}

std::unique_ptr<Shape> make_wedge(double width, double length, double height, double top_width) {
    try {
        BRepPrimAPI_MakeWedge maker(width, length, height, top_width);
        const TopoDS_Shape shape = maker.Shape();
        detail::ensure_valid(shape);
        return std::make_unique<Shape>(shape);
    } catch (const Standard_Failure& failure) {
        detail::rethrow_as_std_error(failure);
    }
}
}  // namespace vg3
