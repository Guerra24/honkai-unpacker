# honkai-unpacker

This is a small java tool that can unpack Honkai Impact 3rd's WMV files. It supports all versions that use them until 4.1. This tool was created without any external input or information.

## File structure

WMV are containers for .unity3d files. Whatever tool miHoYo uses simply smashes the source files into a sequential stream of bytes and then groups them into 50mb files. That's it, there is nothing more to it.

## .unity3d files

Initially the source files would be kept as-is but in all versions after 3.6 the metadata of the .unity3d files is obfuscated. This metadata is possibly kept in one of the companion files (probably `Blocks_<maj>_<min>.xmf`) but this tools does not use it, instead it tries to *guess* the original data and repairs the files. This unfortunately causes the files to become basically unreadable by any standard tool that can open .unity3d files so instead a heavily modified UtinyRipper is used that contains workarounds.

The obfuscated metadata is the following:

 - Version string
 - File size
 - CI Block
 - UI Block
 - Flags

Properly repairing or de obfuscating said data will leave you with a standard unity3d file that can be opened with any tool.

## Output data

The quality of the final data varies. The tools have been optimized to only work with images and still due to the *guessing* it can cause some corruption that is hard to fix.

## Disclaimer

I don't do reverse engineering, this is all based on guesswork and me wanting to learn. I'm only making it public because the supported versions are no longer relevant.
