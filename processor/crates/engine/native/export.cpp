#include "occt_internal.h"
#include "occt.h"

#include <stdexcept>
#include <string>

#include <BRepMesh_IncrementalMesh.hxx>
#include <IFSelect_ReturnStatus.hxx>
#include <STEPCAFControl_Writer.hxx>
#include <StlAPI_Writer.hxx>
#include <Quantity_Color.hxx>
#include <TDataStd_Name.hxx>
#include <TDocStd_Application.hxx>
#include <TDocStd_Document.hxx>
#include <XCAFApp_Application.hxx>
#include <XCAFDoc_ColorTool.hxx>
#include <XCAFDoc_DocumentTool.hxx>
#include <TopoDS_Shape.hxx>

namespace vg3 {
StepBuilder::StepBuilder() {
    Handle(TDocStd_Application) application = XCAFApp_Application::GetApplication();
    Handle(TDocStd_Document) document = new TDocStd_Document("MDTV-XCAF");
    application->NewDocument("MDTV-XCAF", document);
    document_ = document;
    shapes_ = XCAFDoc_DocumentTool::ShapeTool(document_->Main());
    colors_ = XCAFDoc_DocumentTool::ColorTool(document_->Main());
}

void StepBuilder::add_part(
    const Shape& shape,
    rust::Str name,
    bool has_color,
    double r,
    double g,
    double b
) {
    try {
        const TDF_Label label = shapes_->AddShape(shape.topods(), false);
        shapes_->SetShape(label, shape.topods());
        const std::string name_string(name.data(), name.size());
        TDataStd_Name::Set(
            label,
            TCollection_ExtendedString(name_string.c_str(), Standard_True)
        );
        if (has_color) {
            colors_->SetColor(
                label,
                Quantity_Color(r, g, b, Quantity_TOC_RGB),
                XCAFDoc_ColorGen
            );
        }
    } catch (const Standard_Failure& failure) {
        detail::rethrow_as_std_error(failure);
    }
}

bool StepBuilder::write(rust::Str path) {
    try {
        const std::string path_string(path.data(), path.size());
        STEPCAFControl_Writer writer;
        writer.SetColorMode(Standard_True);
        writer.SetNameMode(Standard_True);
        if (!writer.Transfer(document_, STEPControl_AsIs)) {
            throw std::runtime_error("STEPCAFControl_Writer::Transfer did not complete");
        }
        return writer.Write(path_string.c_str()) == IFSelect_RetDone;
    } catch (const Standard_Failure& failure) {
        detail::rethrow_as_std_error(failure);
    }
}

std::unique_ptr<StepBuilder> new_step_builder() {
    return std::make_unique<StepBuilder>();
}

bool write_stl(const Shape& shape, rust::Str path, double tolerance) {
    try {
        const TopoDS_Shape& topods = shape.topods();
        const Standard_Real deflection = tolerance > 0.0 ? tolerance : 0.1;
        BRepMesh_IncrementalMesh mesher(topods, deflection, Standard_False, 0.1, Standard_True);
        StlAPI_Writer writer;
        writer.ASCIIMode() = Standard_False;
        const std::string path_string(path.data(), path.size());
        return writer.Write(topods, path_string.c_str());
    } catch (const Standard_Failure& failure) {
        detail::rethrow_as_std_error(failure);
    }
}

}  // namespace vg3
