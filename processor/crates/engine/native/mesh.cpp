#include "occt_internal.h"
#include "occt.h"

#include <stdexcept>

#include <BRepBndLib.hxx>
#include <BRepGProp.hxx>
#include <BRepMesh_IncrementalMesh.hxx>
#include <BRep_Tool.hxx>
#include <Bnd_Box.hxx>
#include <GProp_GProps.hxx>
#include <Poly_Triangulation.hxx>
#include <TopAbs.hxx>
#include <TopLoc_Location.hxx>
#include <TopExp_Explorer.hxx>
#include <TopoDS.hxx>
#include <TopoDS_Face.hxx>
#include <gp_Pnt.hxx>
#include <gp_Trsf.hxx>

namespace vg3 {
rust::Vec<double> triangulation(const Shape& shape, double tolerance) {
    return detail::attempt([&] {
        const TopoDS_Shape& topods = shape.topods();
        const Standard_Real deflection = tolerance > 0.0 ? tolerance : 0.1;
        BRepMesh_IncrementalMesh mesher(topods, deflection, Standard_False, 0.1, Standard_True);

        rust::Vec<double> data;
        for (TopExp_Explorer explorer(topods, TopAbs_FACE); explorer.More(); explorer.Next()) {
            const TopoDS_Face face = TopoDS::Face(explorer.Current());
            TopLoc_Location location;
            const Handle(Poly_Triangulation) mesh = BRep_Tool::Triangulation(face, location);
            if (mesh.IsNull()) {
                continue;
            }
            const gp_Trsf transformation = location.Transformation();
            for (Standard_Integer index = 1; index <= mesh->NbTriangles(); ++index) {
                Standard_Integer n1 = 0;
                Standard_Integer n2 = 0;
                Standard_Integer n3 = 0;
                mesh->Triangle(index).Get(n1, n2, n3);
                if (face.Orientation() == TopAbs_REVERSED) {
                    std::swap(n2, n3);
                }
                const Standard_Integer nodes[3] = {n1, n2, n3};
                for (const Standard_Integer node : nodes) {
                    const gp_Pnt point = mesh->Node(node).Transformed(transformation);
                    data.push_back(point.X());
                    data.push_back(point.Y());
                    data.push_back(point.Z());
                }
            }
        }
        return data;
    });
}

rust::Vec<double> bounding_box(const Shape& shape) {
    Bnd_Box box;
    BRepBndLib::Add(shape.topods(), box);
    Standard_Real min_x = 0.0;
    Standard_Real min_y = 0.0;
    Standard_Real min_z = 0.0;
    Standard_Real max_x = 0.0;
    Standard_Real max_y = 0.0;
    Standard_Real max_z = 0.0;
    box.Get(min_x, min_y, min_z, max_x, max_y, max_z);
    rust::Vec<double> result;
    result.push_back(min_x);
    result.push_back(min_y);
    result.push_back(min_z);
    result.push_back(max_x);
    result.push_back(max_y);
    result.push_back(max_z);
    return result;
}

double volume(const Shape& shape) {
    GProp_GProps properties;
    BRepGProp::VolumeProperties(shape.topods(), properties);
    return properties.Mass();
}

double surface_area(const Shape& shape) {
    GProp_GProps properties;
    BRepGProp::SurfaceProperties(shape.topods(), properties);
    return properties.Mass();
}
}  // namespace vg3
