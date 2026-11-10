# Third-Party Notices

## Horizon

`core/src/main/kotlin/com/umfluten/lighttimeclouds/sky/SkyModel.kt` (and the sky color table generated
from it at build time) is a Kotlin port of the sky gradient renderer in
[Horizon](https://github.com/dnlzro/horizon), extended with multiple scattering and a spectral
computation. The physical model and its parameters come from ["A Scalable and Production Ready Sky and
Atmosphere Rendering Technique"](https://onlinelibrary.wiley.com/doi/10.1111/cgf.14050) (Sébastien
Hillaire, 2020), whose reference implementation
([UnrealEngineSkyAtmosphere](https://github.com/sebh/UnrealEngineSkyAtmosphere), Copyright (c) 2020
Epic Games, Inc.) is also MIT licensed.

Horizon states that its implementation derives from
["Production Sky Rendering"](https://www.shadertoy.com/view/slSXRW) by Andrew Helmer (Shadertoy, 2021).
That shader's code carries no license notice, but its author stated in the shader's comments on
2021-09-07 that he put it under the MIT License, except for the function `jodieReinhardTonemap()`
taken from another shader; neither Horizon nor this app uses that function (both tone map with
Narkowicz's ACES fit).

```
MIT License

Copyright (c) 2025 Daniel Lazaro

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## Precomputed Atmospheric Scattering

`core/src/main/kotlin/com/umfluten/lighttimeclouds/sky/Spectrum.kt` contains the solar irradiance
and ozone cross-section tables from the demo of
[Precomputed Atmospheric Scattering](https://github.com/ebruneton/precomputed_atmospheric_scattering)
by Eric Bruneton.

```
Copyright (c) 2017 Eric Bruneton
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions
are met:
1. Redistributions of source code must retain the above copyright
   notice, this list of conditions and the following disclaimer.
2. Redistributions in binary form must reproduce the above copyright
   notice, this list of conditions and the following disclaimer in the
   documentation and/or other materials provided with the distribution.
3. Neither the name of the copyright holders nor the names of its
   contributors may be used to endorse or promote products derived from
   this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
THE POSSIBILITY OF SUCH DAMAGE.
```

## Time Zone Database

`core/src/main/resources/com/umfluten/lighttimeclouds/geo/zone.tab` is the unchanged file of the same
name from the [IANA Time Zone Database](https://www.iana.org/time-zones) (release 2026f). It lists the
principal location of each time zone, from which the app suggests a time zone for a shared place. The
file is in the public domain, as stated in its header.
