// .gitignore
require("fs").writeFileSync("F:/Projects/CourseSchedule/.gitignore",
`*.iml
.gradle
/local.properties
/.idea
.DS_Store
/build
/captures
.externalNativeBuild
.cxx
local.properties
/app/build
`);
console.log("gitignore done");

// proguard-rules.pro
require("fs").writeFileSync("F:/Projects/CourseSchedule/app/proguard-rules.pro",
`# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keep class com.example.courseschedule.data.db.entity.** { *; }
`);
console.log("proguard done");