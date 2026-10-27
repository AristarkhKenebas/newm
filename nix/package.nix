# newm, built from these sources with Gradle (offline: its dependencies are listed in
# deps.json, made with `nix build .#default.mitmCache.updateScript && ./result`).
{
  lib,
  stdenv,
  runCommand,
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

  # Gradle's downloads, made once by the update script and checked by their hashes. Until
  # nix/deps.json is made (it is made by the maintainer once the Caulis this newm needs is
  # released), the build stops at once and says how to make it.
  mitmCache =
    let
      deps = gradle.fetchDeps {
        pkg = finalAttrs.finalPackage;
        data = ./deps.json;
      };
    in
    if builtins.pathExists ./deps.json then
      deps
    else
      runCommand "newm-deps-missing" { passthru = { inherit (deps) updateScript; }; } ''
        echo "newm: nix/deps.json (the list of Gradle's downloads) is not there yet." >&2
        echo "Make it in newm's folder: nix build .#default.mitmCache.updateScript && ./result" >&2
        echo "(It needs the Caulis version of gradle/libs.versions.toml on Maven Central; see docs/en/install.md, \"How the Nix package is built\".)" >&2
        exit 1
      '';

  # The build compiles for Java 27 with that JDK (no toolchain downloads in Nix's sandbox).
  gradleFlags = [
    "-Dorg.gradle.java.installations.paths=${jdk}"
    "-Dorg.gradle.java.installations.auto-download=false"
    "-Dorg.gradle.java.installations.auto-detect=false"
    "-Dfile.encoding=utf-8"
    # gradle.properties turns the configuration cache on for everyday builds; Nix's
    # dependency fetcher (an init script) does not work with it.
    "-Dorg.gradle.configuration-cache=false"
  ];
  gradleBuildTask = "installDist";
  # (The tests start a compositor: not in the sandbox.)
  doCheck = false;

  installPhase = ''
    runHook preInstall
    mkdir -p $out/share/newm $out/bin
    cp -r build/install/newm/. $out/share/newm/
    # Its Java, and the libraries it opens by name (EGL, GBM, DRM, libinput, PAM, ...).
    # (The start script's AOT cache lives in ~/.cache/newm/aot, not in the store; its key
    # holds this store path, so each build has its own though every file's time here is 1.)
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
