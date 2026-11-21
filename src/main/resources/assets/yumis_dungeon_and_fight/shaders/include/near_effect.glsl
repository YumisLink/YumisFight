layout(std140) uniform NearEffectSettings {
    vec4 NearEffectFade;
};

// Interpolate position, not vertex distance: large faces can cross the sphere with all corners outside.
float near_effect_visibility(vec3 cameraRelativePosition) {
    float distanceToCamera = length(cameraRelativePosition);
    float fadeDistance = NearEffectFade.x;
#if NEAR_EFFECT_PASS == 1
    if (distanceToCamera < fadeDistance) discard;
    return 1.0;
#else
#if NEAR_EFFECT_PASS == 2
    if (distanceToCamera >= fadeDistance) discard;
#else
    if (distanceToCamera >= fadeDistance) return 1.0;
#endif
    // Scale the original 0.60-to-0.85-block transition with the chosen distance.
    float visibility = smoothstep(fadeDistance * (12.0 / 17.0), fadeDistance, distanceToCamera);
    if (visibility <= 0.0) discard;
    return visibility;
#endif
}
