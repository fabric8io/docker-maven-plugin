FROM alpine

RUN apk add --no-cache gcc libc-dev make cmake tar

ADD ["rootfs.tar", "/usr/src/hello"]

RUN output_file="@builder.output.file@" && \
    source_dir="/usr/src/hello" && \
    build_dir="$(mktemp -d)" && \
    cmake \
      -D CMAKE_SKIP_BUILD_RPATH=ON \
      -D CMAKE_BUILD_TYPE=RELEASE \
      -D CMAKE_USER_MAKE_RULES_OVERRIDE=static.cmake \
      -S "${source_dir}" \
      -B "${build_dir}" && \
    cmake --build "${build_dir}" && \
    mkdir -p "$(dirname "${output_file}")" && \
    tar --sort=name --owner=root:0 --group=root:0 --mtime='UTC 1970-01-01' \
      -C "${build_dir}" -czf "${output_file}" hello && \
    rm -rf "${build_dir}" && \
    echo "Built binaries location: ${output_file}"
