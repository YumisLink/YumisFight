// Interpolate position, not vertex distance: large faces can cross the sphere with all corners outside.
float near_effect_visibility(vec3 cameraRelativePosition) {
    float distanceToCamera = length(cameraRelativePosition);
#if NEAR_EFFECT_PASS == 1
    if (distanceToCamera < 0.85) discard;
    return 1.0;
#else
#if NEAR_EFFECT_PASS == 2
    if (distanceToCamera >= 0.85) discard;
#endif
    float visibility = smoothstep(0.60, 0.85, distanceToCamera);
    if (visibility <= 0.0) discard;
    return visibility;
#endif
}
