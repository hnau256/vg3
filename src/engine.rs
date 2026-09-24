use std::collections::HashSet;

use cxx::UniquePtr;

use crate::error::{Error, Result};
use crate::model::{Model, Node, Operand, TransformOp};
use crate::sys::ffi;

pub struct Part {
    shape: UniquePtr<ffi::Shape>,
}

impl Part {
    pub(crate) fn shape(&self) -> &ffi::Shape {
        self.shape.as_ref().expect("shape handle is never null")
    }
}

pub fn evaluate(model: &Model) -> Result<Vec<Part>> {
    let mut referenced = HashSet::new();
    for node in &model.parts {
        collect_references(node, &mut referenced);
    }

    let mut roots = Vec::new();
    for (index, node) in model.parts.iter().enumerate() {
        if !referenced.contains(&index) {
            roots.push(evaluate_node(node, &model.parts, index)?);
        }
    }
    Ok(roots)
}

fn collect_references(node: &Node, referenced: &mut HashSet<usize>) {
    match node {
        Node::Box { .. } | Node::Sphere { .. } => {}
        Node::Fuse { parts } => {
            for operand in parts {
                collect_references_from_operand(operand, referenced);
            }
        }
        Node::Transform { target, .. } => {
            collect_references_from_operand(target, referenced);
        }
    }
}

fn collect_references_from_operand(operand: &Operand, referenced: &mut HashSet<usize>) {
    match operand {
        Operand::Index(index) => {
            referenced.insert(*index);
        }
        Operand::Inline(node) => collect_references(node, referenced),
    }
}

fn evaluate_node(node: &Node, parts: &[Node], current: usize) -> Result<Part> {
    match node {
        Node::Box {
            width,
            length,
            height,
        } => Ok(Part {
            shape: ffi::make_box(width.value(), length.value(), height.value())?,
        }),
        Node::Sphere { radius } => Ok(Part {
            shape: ffi::make_sphere(radius.value())?,
        }),
        Node::Fuse { parts: operands } => evaluate_fuse(operands, parts, current),
        Node::Transform { target, ops } => evaluate_transform(target, ops, parts, current),
    }
}

fn evaluate_fuse(operands: &[Operand], parts: &[Node], current: usize) -> Result<Part> {
    let mut iter = operands.iter();
    let first = iter.next().ok_or(Error::MissingOperand)?;
    let mut accumulator = evaluate_operand(first, parts, current)?;
    for operand in iter {
        let next = evaluate_operand(operand, parts, current)?;
        let fused = ffi::fuse(accumulator.shape(), next.shape())?;
        accumulator = Part { shape: fused };
    }
    Ok(accumulator)
}

fn evaluate_transform(
    target: &Operand,
    ops: &[TransformOp],
    parts: &[Node],
    current: usize,
) -> Result<Part> {
    let mut part = evaluate_operand(target, parts, current)?;
    for op in ops {
        part = apply_transform(part, op)?;
    }
    Ok(part)
}

fn apply_transform(part: Part, op: &TransformOp) -> Result<Part> {
    match op {
        TransformOp::Translate { value } => {
            let shape = ffi::translate(
                part.shape(),
                value.dx.value(),
                value.dy.value(),
                value.dz.value(),
            )?;
            Ok(Part { shape })
        }
        TransformOp::Rotate { .. } => Err(Error::NotImplemented("transform op: rotate")),
        TransformOp::Mirror { .. } => Err(Error::NotImplemented("transform op: mirror")),
        TransformOp::Scale { .. } => Err(Error::NotImplemented("transform op: scale")),
        TransformOp::Matrix { .. } => Err(Error::NotImplemented("transform op: matrix")),
    }
}

fn evaluate_operand(operand: &Operand, parts: &[Node], current: usize) -> Result<Part> {
    match operand {
        Operand::Index(index) => {
            if *index >= current {
                return Err(Error::InvalidReference {
                    index: *index,
                    current,
                });
            }
            evaluate_node(&parts[*index], parts, *index)
        }
        Operand::Inline(node) => evaluate_node(node, parts, current),
    }
}
