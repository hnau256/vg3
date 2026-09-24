#[allow(clippy::too_many_arguments)]
#[cxx::bridge(namespace = "vg3")]
pub(crate) mod ffi {
    unsafe extern "C++" {
        include!("occt.h");

        type Shape;

        type CompoundBuilder;

        fn new_compound_builder() -> UniquePtr<CompoundBuilder>;

        fn push(self: Pin<&mut CompoundBuilder>, shape: &Shape) -> Result<()>;

        fn finish(self: Pin<&mut CompoundBuilder>) -> Result<UniquePtr<Shape>>;

        type WireBuilder;

        fn new_wire_builder() -> UniquePtr<WireBuilder>;

        fn start(self: Pin<&mut WireBuilder>, x: f64, y: f64, z: f64) -> Result<()>;

        fn line(self: Pin<&mut WireBuilder>, x: f64, y: f64, z: f64) -> Result<()>;

        fn arc(
            self: Pin<&mut WireBuilder>,
            via_x: f64,
            via_y: f64,
            via_z: f64,
            to_x: f64,
            to_y: f64,
            to_z: f64,
        ) -> Result<()>;

        fn spline(self: Pin<&mut WireBuilder>, points: &[f64]) -> Result<()>;

        fn helix(self: Pin<&mut WireBuilder>, pitch: f64, height: f64, right_handed: bool)
            -> Result<()>;

        fn finish(self: Pin<&mut WireBuilder>, closed: bool) -> Result<UniquePtr<Shape>>;

        fn make_box(width: f64, length: f64, height: f64) -> Result<UniquePtr<Shape>>;

        fn make_sphere(radius: f64) -> Result<UniquePtr<Shape>>;

        fn make_cylinder(radius: f64, height: f64) -> Result<UniquePtr<Shape>>;

        fn make_cone(
            radius_bottom: f64,
            radius_top: f64,
            height: f64,
        ) -> Result<UniquePtr<Shape>>;

        fn make_torus(major_radius: f64, minor_radius: f64) -> Result<UniquePtr<Shape>>;

        fn make_wedge(
            width: f64,
            length: f64,
            height: f64,
            top_width: f64,
        ) -> Result<UniquePtr<Shape>>;

        fn make_halfspace() -> Result<UniquePtr<Shape>>;

        fn extrude(profile: &Shape, height: f64) -> Result<UniquePtr<Shape>>;

        fn revolve(profile: &Shape, angle: f64) -> Result<UniquePtr<Shape>>;

        fn sweep(profile: &Shape, spine: &Shape, follow: bool) -> Result<UniquePtr<Shape>>;

        type LoftBuilder;

        fn new_loft_builder(ruled: bool) -> UniquePtr<LoftBuilder>;

        fn add(self: Pin<&mut LoftBuilder>, section: &Shape) -> Result<()>;

        fn finish(self: Pin<&mut LoftBuilder>) -> Result<UniquePtr<Shape>>;

        fn triangulation(shape: &Shape, tolerance: f64) -> Result<Vec<f64>>;

        fn bounding_box(shape: &Shape) -> Vec<f64>;

        fn volume(shape: &Shape) -> f64;

        fn solid_edge_count(shape: &Shape, solid_index: usize) -> usize;

        fn solid_edge_data(shape: &Shape, solid_index: usize, edge_index: usize) -> Vec<f64>;

        fn fillet(shape: &Shape, kind: u8, values: &[f64]) -> Result<UniquePtr<Shape>>;

        fn fuse(a: &Shape, b: &Shape) -> Result<UniquePtr<Shape>>;

        fn cut(a: &Shape, b: &Shape) -> Result<UniquePtr<Shape>>;

        fn common(a: &Shape, b: &Shape) -> Result<UniquePtr<Shape>>;

        fn translate(shape: &Shape, x: f64, y: f64, z: f64) -> Result<UniquePtr<Shape>>;

        fn rotate(
            shape: &Shape,
            center_x: f64,
            center_y: f64,
            center_z: f64,
            axis_x: f64,
            axis_y: f64,
            axis_z: f64,
            angle: f64,
        ) -> Result<UniquePtr<Shape>>;

        fn mirror(
            shape: &Shape,
            center_x: f64,
            center_y: f64,
            center_z: f64,
            normal_x: f64,
            normal_y: f64,
            normal_z: f64,
        ) -> Result<UniquePtr<Shape>>;

        fn scale(shape: &Shape, x: f64, y: f64, z: f64) -> Result<UniquePtr<Shape>>;

        fn apply_matrix(shape: &Shape, matrix: &[f64]) -> Result<UniquePtr<Shape>>;

        fn is_solids_only(shape: &Shape) -> bool;

        fn solid_count(shape: &Shape) -> usize;

        fn write_stl(shape: &Shape, path: &str, tolerance: f64) -> Result<bool>;
    }
}
