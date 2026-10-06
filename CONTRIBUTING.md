# Contributing to Budík OS

Thanks for taking the time to help.

## Reporting bugs

Open an issue with the bug report template. Include the build number (Settings > About phone), the device and steps to reproduce. Attach a logcat when the problem is a crash:

```
adb logcat -b all -d > logcat.txt
```

One issue per problem, please.

## Pull requests

1. Fork the repository and create a branch from `main`.
2. Keep each pull request focused on one change.
3. Follow the style of the code around your change. For AOSP code that means the AOSP style guide.
4. Describe what you changed and how you tested it, including the device.
5. Sign off your commits (`git commit -s`) to confirm you have the right to submit the code under the project license ([Developer Certificate of Origin](https://developercertificate.org)).

All pull requests need a review from a maintainer before they are merged. Direct pushes to `main` are disabled.

## Where to send changes

- Build scripts, `vendor/budikos`, the kernel patches and tools: pull request against `main` in this repository.
- Android projects (SystemUI, Settings, frameworks and so on): pull request against the `budik-17` branch of the matching fork, for example [BudikOS/platform_frameworks_base](https://github.com/BudikOS/platform_frameworks_base). The manifest file [`budikos.xml`](https://github.com/BudikOS/platform_manifest/blob/budik-17/budikos.xml) maps every path in the tree to its fork. Merged changes are also exported to `patches/` in this repository, which the build scripts apply.
- A project that has no fork yet: open an issue or a pull request here with the patch under `patches/<project path>/`.

## Design

The interface follows the Budík design system. UI changes should match it: 6, 12 or 20 dp corner radii, hard offset shadows, no blur or gradients, and text that stays readable in both light and dark mode. When in doubt, open an issue with a screenshot before writing code.

## Adding a device

New devices are welcome. Open an issue first with the device name, codename, chipset, kernel version and whether the bootloader can be unlocked, so we can agree on the approach.
