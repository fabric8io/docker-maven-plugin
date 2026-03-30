FROM alpine AS base
RUN echo "base" > /stage.txt
CMD ["sh", "-c", "cat /stage.txt && sleep infinity"]

FROM base AS final
RUN echo "final" > /stage.txt
