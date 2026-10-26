# newm, built from these sources with Gradle (offline: its dependencies are listed in
# deps.json, made with `nix build .#default.mitmCache.updateScript && ./result`).
{
  lib,
  stdenv,
  gradle_9,
  makeWrapper,
  # JDK 27 (Caulis' flake has one until nixpkgs does).
  jdk,
  # The C libraries Caulis looks up while running (Caulis' flake: lib.libraries pkgs).
  libraries,
  pam,
  fontconfig,
  freetype,
}:

let
  gradle = gradle_9;
in
stdenv.mkDerivation (finalAttrs: {
  pname = "newm";
  version = "0.1.0";

  src = lib.cleanSourceWith {
    src = ../.;
    # Only what the build reads (not build/, not a heap dump lying around).
    filter = path: type:
      let name = baseNameOf path; in
      !(lib.hasSuffix ".hprof" name) && name != "build" && name != ".gradle" && name != "result" && name != ".kotlin";
  };

  nativeBuildInputs = [ gradle makeWrapper ];

  # Gradle's downloads, made once by the update script and checked by their hashes.
  mitmCache = gradle.fetchDeps {
    pkg = finalAttrs.finalPackage;
    data = ./deps.json;
  };

  # The build compiles for Java 27 with that JDK (no toolchain downloads in Nix's sandbox).
  gradleFlags = [
    "-Dorg.gradle.java.installations.paths=${jdk}"
    "-Dorg.gradle.java.installations.auto-download=false"
    "-Dorg.gradle.java.installations.auto-detect=false"
    "-Dfile.encoding=utf-8"
  ];
  gradleBuildTask = "installDist";
  # (The tests start a compositor: not in the sandbox.)
  doCheck = false;

  installPhase = ''
    runHook preInstall
    mkdir -p $out/share/newm $out/bin
    cp -r build/install/newm/. $out/share/newm/
    # Its Java, and the libraries it opens by name (EGL, GBM, DRM, libinput, PAM, ...).
    makeWrapper $out/share/newm/bin/newm $out/bin/newm \
      --set JAVA_HOME ${jdk} \
      --prefix LD_LIBRARY_PATH : ${lib.makeLibraryPath (libraries ++ [ pam fontconfig freetype ])}
    runHook postInstall
  '';

  meta = {
    description = "Wayland compositor of an endless wall of windows, made for touchpads (newm on Caulis)";
    homepage = "https://github.com/AristarkhKenebas/newm";
    license = lib.licenses.mit;
    platforms = lib.platforms.linux;
    mainProgram = "newm";
  };
})
