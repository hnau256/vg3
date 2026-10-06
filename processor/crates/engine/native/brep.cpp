#include "occt_internal.h"
#include "occt.h"

#include <stdexcept>

#include <BRepTools.hxx>
#include <Standard_Failure.hxx>
#include <TopoDS_Shape.hxx>

namespace vg3 {
rust::Vec<std::uint8_t> brep_encode(const Shape& shape) {
    return detail::attempt([&] {
        std::ostringstream stream;
        BRepTools::Write(
            shape.topods(),
            stream,
            Standard_False,
            Standard_False,
            TopTools_FormatVersion_CURRENT
        );
        const std::string data = stream.str();
        rust::Vec<std::uint8_t> bytes;
        bytes.reserve(data.size());
        for (const char byte : data) {
            bytes.push_back(static_cast<std::uint8_t>(byte));
        }
        return bytes;
    });
}

std::unique_ptr<Shape> brep_decode(rust::Slice<const std::uint8_t> bytes) {
    return std::make_unique<Shape>(detail::build([&] {
        const std::string data(reinterpret_cast<const char*>(bytes.data()), bytes.size());
        std::istringstream stream(data);
        TopoDS_Shape shape;
        BRep_Builder builder;
        BRepTools::Read(shape, stream, builder);
        if (shape.IsNull()) {
            throw std::runtime_error("BREP data contains a null shape");
        }
        return shape;
    }));
}

}  // namespace vg3
