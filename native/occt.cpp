#include "occt.h"

#include <cmath>
#include <stdexcept>
#include <string>

#include <Bnd_Box.hxx>
#include <BRepAlgoAPI_Fuse.hxx>
#include <BRepBndLib.hxx>
#include <BRepBuilderAPI_Transform.hxx>
#include <BRepCheck_Analyzer.hxx>
#include <BRepMesh_IncrementalMesh.hxx>
#include <BRepPrimAPI_MakeBox.hxx>
#include <BRepPrimAPI_MakeSphere.hxx>
#include <Standard_Failure.hxx>
#include <StlAPI_Writer.hxx>
#include <TopoDS_Shape.hxx>
#include <gp_Trsf.hxx>
#include <gp_Vec.hxx>

namespace vg3 {

namespace {

[[noreturn]] void rethrow_as_std_error(const Standard_Failure& failure) {
    throw std::runtime_error(failure.GetMessageString());
}

void ensure_valid(const TopoDS_Shape& shape) {
    BRepCheck_Analyzer analyzer(shape);
    if (!analyzer.IsValid()) {
        throw std::runtime_error("OpenCASCADE produced an invalid shape");
    }
}

Standard_Real resolve_linear_deflection(const TopoDS_Shape& shape) {
    Bnd_Box bounding_box;
    BRepBndLib::Add(shape, bounding_box);
    if (bounding_box.IsVoid()) {
        return 0.1;
    }
    const Standard_Real deflection = std::sqrt(bounding_box.SquareExtent()) * 1e-3;
    return deflection > 0.0 ? deflection : 0.1;
}

void mesh(const TopoDS_Shape& shape) {
    BRepMesh_IncrementalMesh mesher(
        shape,
        resolve_linear_deflection(shape),
        Standard_False,
        0.1,
        Standard_True
    );
}

}  // namespace

Shape::Shape(const TopoDS_Shape& shape) : shape_(shape) {}

const TopoDS_Shape& Shape::topods() const {
    return shape_;
}

std::unique_ptr<Shape> make_box(double width, double length, double height) {
    try {
        BRepPrimAPI_MakeBox maker(width, length, height);
        const TopoDS_Shape shape = maker.Shape();
        ensure_valid(shape);
        return std::make_unique<Shape>(shape);
    } catch (const Standard_Failure& failure) {
        rethrow_as_std_error(failure);
    }
}

std::unique_ptr<Shape> make_sphere(double radius) {
    try {
        BRepPrimAPI_MakeSphere maker(radius);
        const TopoDS_Shape shape = maker.Shape();
        ensure_valid(shape);
        return std::make_unique<Shape>(shape);
    } catch (const Standard_Failure& failure) {
        rethrow_as_std_error(failure);
    }
}

std::unique_ptr<Shape> translate(const Shape& shape, double x, double y, double z) {
    try {
        gp_Trsf transformation;
        transformation.SetTranslation(gp_Vec(x, y, z));
        BRepBuilderAPI_Transform builder(shape.topods(), transformation, true);
        const TopoDS_Shape result = builder.Shape();
        ensure_valid(result);
        return std::make_unique<Shape>(result);
    } catch (const Standard_Failure& failure) {
        rethrow_as_std_error(failure);
    }
}

std::unique_ptr<Shape> fuse(const Shape& a, const Shape& b) {
    try {
        BRepAlgoAPI_Fuse operation(a.topods(), b.topods());
        operation.Build();
        if (!operation.IsDone()) {
            throw std::runtime_error("BRepAlgoAPI_Fuse did not complete");
        }
        const TopoDS_Shape result = operation.Shape();
        ensure_valid(result);
        return std::make_unique<Shape>(result);
    } catch (const Standard_Failure& failure) {
        rethrow_as_std_error(failure);
    }
}

bool write_stl(const Shape& shape, rust::Str path) {
    try {
        const TopoDS_Shape& topods = shape.topods();
        mesh(topods);
        StlAPI_Writer writer;
        writer.ASCIIMode() = Standard_False;
        const std::string path_string(path.data(), path.size());
        return writer.Write(topods, path_string.c_str());
    } catch (const Standard_Failure& failure) {
        rethrow_as_std_error(failure);
    }
}

}  // namespace vg3
