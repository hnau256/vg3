#pragma once

// Shared helpers for the OCCT bridge implementation. Not part of the cxx bridge (see occt.h).

#include <cstddef>

#include <Standard_Failure.hxx>
#include <TopTools_IndexedDataMapOfShapeListOfShape.hxx>
#include <TopoDS_Edge.hxx>
#include <TopoDS_Shape.hxx>

namespace vg3 {

class Shape;

namespace detail {

constexpr double kPointTolerance = 1e-7;
constexpr double kOffsetTolerance = 1e-3;

/// Translates an OCCT failure into the `std::runtime_error` the cxx bridge expects.
[[noreturn]] void rethrow_as_std_error(const Standard_Failure& failure);

/// Throws if the shape fails `BRepCheck_Analyzer` (fail loudly, never a silent bad shape).
void ensure_valid(const TopoDS_Shape& shape);

/// True when the shape is a solid or a compound consisting solely of solids.
bool solids_only(const TopoDS_Shape& shape);

/// The `index`-th solid of `shape` (throws when out of range).
TopoDS_Shape nth_solid(const TopoDS_Shape& shape, std::size_t index);

/// True when a face of the edge is closed on it (a surface seam — excluded from `fillet`).
bool is_seam_edge(
    const TopoDS_Edge& edge,
    const TopTools_IndexedDataMapOfShapeListOfShape& edge_faces
);

}  // namespace detail

}  // namespace vg3
