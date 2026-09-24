#pragma once

#include <cstdint>
#include <memory>

#include <rust/cxx.h>

#include <TopoDS_Shape.hxx>

namespace vg3 {

class Shape {
public:
    explicit Shape(const TopoDS_Shape& shape);

    const TopoDS_Shape& topods() const;

private:
    TopoDS_Shape shape_;
};

std::unique_ptr<Shape> make_box(double width, double length, double height);

std::unique_ptr<Shape> make_sphere(double radius);

std::unique_ptr<Shape> translate(const Shape& shape, double x, double y, double z);

std::unique_ptr<Shape> fuse(const Shape& a, const Shape& b);

bool write_stl(const Shape& shape, rust::Str path);

}  // namespace vg3
