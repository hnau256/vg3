#pragma once

// Shared helpers for the OCCT bridge implementation. Not part of the cxx bridge (see occt.h).

#include <cstddef>

#include <Standard_Failure.hxx>
#include <Approx_ParametrizationType.hxx>
#include <BRepBuilderAPI_TransitionMode.hxx>
#include <GeomAbs_JoinType.hxx>
#include <GeomAbs_Shape.hxx>
#include <TopTools_IndexedDataMapOfShapeListOfShape.hxx>
#include <TopoDS_Edge.hxx>
#include <TopoDS_Shape.hxx>

namespace vg3 {

class Shape;

namespace detail {

constexpr double kPointTolerance = 1e-7;
constexpr double kOffsetTolerance = 1e-3;

/// Maps the IR join kind (`0` = arc, `1` = tangent, `2` = intersection) to `GeomAbs_JoinType`.
inline GeomAbs_JoinType join_type(std::uint8_t code) {
    switch (code) {
        case 1:
            return GeomAbs_Tangent;
        case 2:
            return GeomAbs_Intersection;
        default:
            return GeomAbs_Arc;
    }
}

/// Maps the IR transition kind (`0` = right corner, `1` = transformed, `2` = round) to
/// `BRepBuilderAPI_TransitionMode`.
inline BRepBuilderAPI_TransitionMode transition_mode(std::uint8_t code) {
    switch (code) {
        case 1:
            return BRepBuilderAPI_Transformed;
        case 2:
            return BRepBuilderAPI_RoundCorner;
        default:
            return BRepBuilderAPI_RightCorner;
    }
}

/// Maps the IR continuity (`0..3` = C0..C3) to `GeomAbs_Shape`.
inline GeomAbs_Shape continuity_shape(std::uint8_t code) {
    switch (code) {
        case 0:
            return GeomAbs_C0;
        case 2:
            return GeomAbs_C2;
        case 3:
            return GeomAbs_C3;
        default:
            return GeomAbs_C1;
    }
}

/// Maps the IR parametrization (`0` = chord length, `1` = centripetal, `2` = iso) to
/// `Approx_ParametrizationType`.
inline Approx_ParametrizationType parametrization_type(std::uint8_t code) {
    switch (code) {
        case 1:
            return Approx_Centripetal;
        case 2:
            return Approx_IsoParametric;
        default:
            return Approx_ChordLength;
    }
}

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

/// Runs `build_shape`, validates the result and rethrows OCCT failures as `std::runtime_error` —
/// the fail-loudly tail shared by every shape builder.
template <typename F>
TopoDS_Shape build(F&& build_shape) {
    try {
        const TopoDS_Shape shape = build_shape();
        ensure_valid(shape);
        return shape;
    } catch (const Standard_Failure& failure) {
        rethrow_as_std_error(failure);
    }
}

/// Runs `action`, translating an OCCT failure into `std::runtime_error` (for operations that do not
/// produce a shape, e.g. writers).
template <typename F>
auto attempt(F&& action) {
    try {
        return action();
    } catch (const Standard_Failure& failure) {
        rethrow_as_std_error(failure);
    }
}

}  // namespace detail

}  // namespace vg3
