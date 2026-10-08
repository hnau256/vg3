#include "occt_internal.h"
#include "occt.h"

#include <stdexcept>

#include <BRepAdaptor_Surface.hxx>
#include <BRepGProp.hxx>
#include <BRepLProp_SLProps.hxx>
#include <GProp_GProps.hxx>
#include <ShapeUpgrade_UnifySameDomain.hxx>
#include <TopExp.hxx>
#include <TopExp_Explorer.hxx>
#include <TopAbs.hxx>
#include <TopTools_IndexedMapOfShape.hxx>
#include <TopoDS.hxx>
#include <TopoDS_Face.hxx>
#include <gp_Dir.hxx>
#include <gp_Pnt.hxx>
#include <gp_Vec.hxx>

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

std::size_t edge_count(const Shape& shape) {
    TopTools_IndexedMapOfShape edges;
    TopExp::MapShapes(shape.topods(), TopAbs_EDGE, edges);
    return edges.Extent();
}

rust::Vec<double> face_data(const Shape& shape, std::size_t index) {
    TopoDS_Face face;
    std::size_t current = 0;
    for (TopExp_Explorer explorer(shape.topods(), TopAbs_FACE); explorer.More();
         explorer.Next()) {
        if (current == index) {
            face = TopoDS::Face(explorer.Current());
            break;
        }
        ++current;
    }
    if (face.IsNull()) {
        throw std::runtime_error("face index is out of range");
    }

    GProp_GProps properties;
    BRepGProp::SurfaceProperties(face, properties);
    const gp_Pnt center = properties.CentreOfMass();

    gp_Vec normal(0.0, 0.0, 0.0);
    BRepAdaptor_Surface surface(face);
    const double u = 0.5 * (surface.FirstUParameter() + surface.LastUParameter());
    const double v = 0.5 * (surface.FirstVParameter() + surface.LastVParameter());
    BRepLProp_SLProps local(surface, u, v, 1, detail::kPointTolerance);
    if (local.IsNormalDefined()) {
        gp_Dir direction = local.Normal();
        if (face.Orientation() == TopAbs_REVERSED) {
            direction.Reverse();
        }
        normal = gp_Vec(direction);
    }

    const detail::Bounds bounds = detail::bounds(face);

    rust::Vec<double> data;
    data.push_back(normal.X());
    data.push_back(normal.Y());
    data.push_back(normal.Z());
    data.push_back(center.X());
    data.push_back(center.Y());
    data.push_back(center.Z());
    data.push_back(properties.Mass());
    data.push_back(bounds.min.X());
    data.push_back(bounds.min.Y());
    data.push_back(bounds.min.Z());
    data.push_back(bounds.max.X());
    data.push_back(bounds.max.Y());
    data.push_back(bounds.max.Z());
    return data;
}

std::unique_ptr<Shape> unify(const Shape& shape) {
    return std::make_unique<Shape>(detail::build([&] {
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
        return result;
    }));
}
}  // namespace vg3
