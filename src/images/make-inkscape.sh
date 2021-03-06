#!/bin/sh -e
#
# The MIT License
#
# Copyright (c) 2011-2014, CloudBees, Inc.
#
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:
#
# The above copyright notice and this permission notice shall be included in
# all copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
# THE SOFTWARE.
#

dir=$(dirname $0)
for src in "$dir"/*.svg
do
  echo "Processing $(basename "$src")..."
  file=$(basename "$src" | sed -e s/.svg/.png/ )
  for sz in 16 24 32 48
  do
    dst="${dir}/../../src/main/webapp/images/${sz}x${sz}/${file}"
    if [ ! -e "$dst" -o "$src" -nt "$dst" ]
    then
      echo -n "  generating ${sz}x${sz}..."
      mkdir -p "${dir}/../../src/main/webapp/images/${sz}x${sz}" > /dev/null 2>&1 || true
      inkscape -z -C -w ${sz} -h ${sz} -e "$dst" "$src" 2>&1 | grep "Bitmap saved as"
    fi
  done
done
