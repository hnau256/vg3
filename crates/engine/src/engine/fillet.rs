use vg3_model::{FilletKind, RadiusSpec};

use crate::engine::part::{make_part, Part};
use crate::error::{Error, Result};
use crate::sys::ffi;

/// Fillets or chamfers every edge of `target`, calling the Rhai expression for each edge.
pub(super) fn evaluate_fillet(target: Part, kind: FilletKind, radius: &RadiusSpec) -> Result<Part> {
    let shape = target.shape();
    let engine = expression_engine();
    let mut values = Vec::new();
    for solid in 0..ffi::solid_count(shape) {
        for edge in 0..ffi::solid_edge_count(shape, solid) {
            let data = ffi::solid_edge_data(shape, solid, edge);
            let value = match radius {
                RadiusSpec::All { radius } => radius.value(),
                RadiusSpec::Expression { expression } => {
                    evaluate_expression(&engine, expression, &data)?
                }
            };
            values.push(value);
        }
    }
    let kind_code = match kind {
        FilletKind::Fillet => 0,
        FilletKind::Chamfer => 1,
    };
    make_part(ffi::fillet(shape, kind_code, &values)?)
}

fn expression_engine() -> rhai::Engine {
    let mut engine = rhai::Engine::new();
    engine.set_max_operations(10_000);
    engine
}

fn evaluate_expression(engine: &rhai::Engine, expression: &str, data: &[f64]) -> Result<f64> {
    let mut scope = rhai::Scope::new();
    scope.push_constant("edge", build_edge(data));
    let result = engine
        .eval_expression_with_scope::<rhai::Dynamic>(&mut scope, expression)
        .map_err(|error| Error::Expression(format!("{expression}: {error}")))?;
    let value = if let Some(number) = result.clone().try_cast::<f64>() {
        number
    } else if let Some(number) = result.clone().try_cast::<i64>() {
        number as f64
    } else {
        return Err(Error::Expression(format!(
            "{expression}: result is not a number"
        )));
    };
    if value.is_nan() {
        return Err(Error::Expression(format!("{expression}: result is NaN")));
    }
    Ok(value)
}

fn build_edge(data: &[f64]) -> rhai::Map {
    let mut edge = rhai::Map::new();
    edge.insert("length".into(), rhai::Dynamic::from(data[0]));
    edge.insert(
        "curve_type".into(),
        rhai::Dynamic::from(curve_type_name(data[1]).to_string()),
    );
    edge.insert("is_vertical".into(), rhai::Dynamic::from(data[2] != 0.0));
    edge.insert("is_horizontal".into(), rhai::Dynamic::from(data[3] != 0.0));

    let mut direction = rhai::Map::new();
    direction.insert("dx".into(), rhai::Dynamic::from(data[4]));
    direction.insert("dy".into(), rhai::Dynamic::from(data[5]));
    direction.insert("dz".into(), rhai::Dynamic::from(data[6]));
    edge.insert("direction".into(), rhai::Dynamic::from(direction));

    edge.insert("radius".into(), rhai::Dynamic::from(data[7]));

    let mut start = rhai::Map::new();
    start.insert("x".into(), rhai::Dynamic::from(data[8]));
    start.insert("y".into(), rhai::Dynamic::from(data[9]));
    start.insert("z".into(), rhai::Dynamic::from(data[10]));
    edge.insert("start".into(), rhai::Dynamic::from(start));

    let mut end = rhai::Map::new();
    end.insert("x".into(), rhai::Dynamic::from(data[11]));
    end.insert("y".into(), rhai::Dynamic::from(data[12]));
    end.insert("z".into(), rhai::Dynamic::from(data[13]));
    edge.insert("end".into(), rhai::Dynamic::from(end));

    edge
}

fn curve_type_name(code: f64) -> &'static str {
    match code as i32 {
        0 => "line",
        1 => "arc",
        _ => "spline",
    }
}
