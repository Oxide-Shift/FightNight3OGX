# XZ for Java

The 7z disc import (`android/src/org/oxideshift/corner/SevenZip.java`) decodes LZMA, LZMA2 and the branch and delta
filters with XZ for Java 1.10, bundled as unmodified source in `android/src/org/tukaani/xz/`.

- Project: https://tukaani.org/xz/java.html (https://github.com/tukaani-project/xz-java)
- Source: `org.tukaani:xz:1.10` sources JAR from Maven Central, SHA-1 `da40ce96732b8b24c994218f5f55a04d6aca4c00`
- Authors: Lasse Collin, Igor Pavlov, Brett Okken and the XZ for Java contributors
- License: BSD Zero Clause License (0BSD), as stated in each file's `SPDX-License-Identifier` header:

> Permission to use, copy, modify, and/or distribute this software for any purpose with or without fee is hereby
> granted.
>
> THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES WITH REGARD TO THIS SOFTWARE INCLUDING ALL
> IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY SPECIAL, DIRECT,
> INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN
> AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR
> PERFORMANCE OF THIS SOFTWARE.

The Java 9+ multi-release variants in the original JAR (`META-INF/versions/9`) are not included; the app builds with
`--release 8` and uses the portable versions.
