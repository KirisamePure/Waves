#version 120
varying vec4 tint;
void main() { gl_Position = ftransform(); tint = gl_Color; }
