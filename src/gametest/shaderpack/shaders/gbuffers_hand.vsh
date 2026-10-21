#version 120
varying vec2 uv;
varying vec2 lm;
varying vec4 tint;
varying vec3 normalView;
varying vec3 positionView;
void main() {
    vec4 view = gl_ModelViewMatrix * gl_Vertex;
    gl_Position = gl_ProjectionMatrix * view;
    uv = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    lm = (gl_TextureMatrix[1] * gl_MultiTexCoord1).xy;
    tint = gl_Color;
    normalView = normalize(gl_NormalMatrix * gl_Normal);
    positionView = view.xyz;
}
