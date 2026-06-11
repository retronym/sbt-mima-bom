enablePlugins(MimaBomPlugin)

bomCompatPreviousFile := Some(baseDirectory.value / "previous-pom.xml")
bomCompatCurrentFile := baseDirectory.value / "current-pom.xml"
