// vg3: azimuth=35 elevation=25
val plate = box(Axis3Values(x = 20.0, y = 20.0, z = 5.0))
val boss = cylinder(radius = 5.0, height = 10.0).translate(Axis3.Z.direction * 5.0)
(plate + boss).filletSelected(expression = "is_parallel(edge.direction, Z)", radius = 2.0)
