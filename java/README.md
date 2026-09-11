# YSoccer

A [libGDX](https://libgdx.com/) project generated with [gdx-liftoff](https://github.com/libgdx/gdx-liftoff).

This project was generated with a template including simple application launchers and an `ApplicationAdapter` extension that draws libGDX logo.

## Platforms

- `core`: Main module with the application logic shared by all platforms.
- `lwjgl3`: Primary desktop platform using LWJGL3; was called 'desktop' in older docs.
- `android`: Android mobile platform. Needs Android SDK.

## Xbox controllers

Controllers with a backend mapping work immediately, including when connected after startup.
The default layout is left stick or D-pad for movement/menu navigation, A for action 1/confirmation,
B for action 2/the menu's alternate action, X for switching players, and Start for match pause/resume.
B retains the existing secondary-action behavior; menu exit buttons remain selectable with the D-pad.

Saved custom bindings take precedence. In **Controls**, select the controller and use its red reset
button to restore the standard mapping. Button fields show A/B/X/Y/LB/RB where recognized.
Selecting an occupied action button swaps its binding with the action being edited.

The default stick dead zone is 30%. Adjust **Stick dead zone** using action 1 (+5%) and action 2 (-5%),
within 10–80%; increase it for drift or lower it for lighter movement. When rebinding an axis, let the
stick return to center, then move firmly in the requested direction; inversion is detected automatically.
Press Escape to cancel capture. Release controls after connection or rebinding before operating menus.
The **Left / right** and **Up / down** buttons explicitly show normal/reversed polarity and toggle it
without resetting action bindings. **Direction** previews the same input values used by gameplay.
The standard Xbox mapping uses positive X for right and negative Y for up; both polarity settings
should normally be **Normal**. Use the preview to verify manual calibration before starting a match.

Disconnecting clears held inputs. Reconnecting restores the existing player slot, preferring the
backend's device ID and falling back to a disconnected controller of the same model when IDs change.
Bindings are shared by controller model, while capture targets the selected physical device.
Previously saved configurations for unplugged models are retained when editing connected devices.

Run the hardware-free input regression suite with `gradlew.bat :core:controllerRegressionTest`.
It is also included in `:core:check`. Hardware validation should cover a real match, bench navigation,
USB/Bluetooth reconnection, and two Xbox controllers connected at once.
`:core:warningRegressionTest` checks the competition-loss messages in all bundled translations;
it is included in `:core:check` as well.

## Gradle

This project uses [Gradle](https://gradle.org/) to manage dependencies.
The Gradle wrapper was included, so you can run Gradle tasks using `gradlew.bat` or `./gradlew` commands.
Useful Gradle tasks and flags:

- `--continue`: when using this flag, errors will not stop the tasks from running.
- `--daemon`: thanks to this flag, Gradle daemon will be used to run chosen tasks.
- `--offline`: when using this flag, cached dependency archives will be used.
- `--refresh-dependencies`: this flag forces validation of all dependencies. Useful for snapshot versions.
- `android:lint`: performs Android project validation.
- `build`: builds sources and archives of every project.
- `cleanEclipse`: removes Eclipse project data.
- `cleanIdea`: removes IntelliJ project data.
- `clean`: removes `build` folders, which store compiled classes and built archives.
- `eclipse`: generates Eclipse project data.
- `idea`: generates IntelliJ project data.
- `lwjgl3:jar`: builds application's runnable jar, which can be found at `lwjgl3/build/libs`.
- `lwjgl3:run`: starts the application.
- `test`: runs unit tests (if any).

Note that most tasks that are not specific to a single project can be run with `name:` prefix, where the `name` should be replaced with the ID of a specific project.
For example, `core:clean` removes `build` folder only from the `core` project.
