<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/images/header-dark.png">
  <img alt="Budík OS" src="docs/images/header-light.png">
</picture>

Budík OS is an Android 17 based operating system for phones that the manufacturers stopped updating. It runs a current Android release on older hardware, with its own interface, a tuned kernel and a small memory footprint.

The system is built from the GrapheneOS source tree as a Generic System Image, with TrebleDroid and RestlessOS patches that let Android 17 run on older kernels and vendor images. Device-specific work (kernel, overlays, SELinux fixes) lives separately for each phone.

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/images/screens-dark.png">
  <img alt="Screenshots" src="docs/images/screens-light.png">
</picture>

## Status

Early development. Builds boot and run, but expect rough edges and breaking changes between builds. Do not install it on a phone you cannot afford to wipe.

## Devices

| Device | Codename | Status |
| --- | --- | --- |
| Motorola moto g7 power | `ocean` | In development |
| Samsung Galaxy A12 | `a12` | Planned |
| Samsung Galaxy A13 | `a13` | Planned |

More devices will be added over time. Because the system part is a GSI, most Treble-compliant phones with an unlockable bootloader are realistic targets.

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/images/what-works-dark.png">
  <img alt="What works" src="docs/images/what-works-light.png">
</picture>

Tested on `ocean`:

- [x] Boot with the Budík kernel
- [x] Camera and flashlight
- [x] Split shade: notifications on the left, control centre on the right
- [x] Performance profiles (battery, balanced, performance) and compressed RAM swap
- [x] Setup wizard
- [ ] Calls, mobile data, Wi-Fi, Bluetooth and GPS are being tested

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/images/known-issues-dark.png">
  <img alt="Known issues" src="docs/images/known-issues-light.png">
</picture>

- Lock screen and home screen still use the stock layout; the redesign is in progress.
- Some system apps (Phone, Messages, Files) do not follow the system design yet.
- RCS needs Google Messages, which can be installed through sandboxed Google Play.
- The bootloader stays unlocked, so Play Integrity reports basic integrity only.

## Building

Build scripts, patches and the device trees will be published here soon. The system image is built with the regular Android build system from the GrapheneOS tree; a full sync needs about 250 GB of disk space and 64 GB of RAM is recommended.

## Contributing

Bug reports and pull requests are welcome. Please read [CONTRIBUTING.md](CONTRIBUTING.md) first. Security issues go through [SECURITY.md](SECURITY.md), not public issues.

## Related projects

- [budikstore.org](https://budikstore.org)
- [erp.budikstore.org](https://erp.budikstore.org)

## Credits

Budík OS would not exist without [AOSP](https://source.android.com), [GrapheneOS](https://grapheneos.org), [TrebleDroid](https://github.com/TrebleDroid), [RestlessOS](https://github.com/cawilliamson/treble_restlessos) and [LineageOS](https://lineageos.org). Fonts: [Schibsted Grotesk](https://github.com/schibsted/schibsted-grotesk) and [Instrument Sans](https://github.com/Instrument/instrument-sans), both under the SIL Open Font License.

## License

Budík OS is licensed under the [GNU General Public License v3.0](LICENSE). Code taken from upstream projects keeps its original license: AOSP and GrapheneOS components are Apache 2.0, kernel patches are GPL-2.0.
