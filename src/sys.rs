#[cxx::bridge(namespace = "vg3")]
pub(crate) mod ffi {
    unsafe extern "C++" {
        include!("occt.h");

        type Shape;

        fn make_box(width: f64, length: f64, height: f64) -> Result<UniquePtr<Shape>>;

        fn make_sphere(radius: f64) -> Result<UniquePtr<Shape>>;

        fn translate(shape: &Shape, x: f64, y: f64, z: f64) -> Result<UniquePtr<Shape>>;

        fn fuse(a: &Shape, b: &Shape) -> Result<UniquePtr<Shape>>;

        fn write_stl(shape: &Shape, path: &str) -> Result<bool>;
    }
}
