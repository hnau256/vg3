// vg3: azimuth=35 elevation=25
val plate = box(width = 20.0, length = 20.0, height = 5.0)
val boss = cylinder(radius = 5.0, height = 10.0).translate(0.0, 0.0, 5.0)
(plate + boss).filletSelected(expression = "is_parallel(edge.direction, Z)", radius = 2.0)
