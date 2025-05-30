### Sistema distribuído mínimo escrito em Java.
Implementa:
> - Comunicação unicast por protocolo TCP
> - Comunicação multicast por protocolo UDP.

Para representação externa dos dados, foi usado **Protocol Buffers**, devido a seus carater estruturado e seguro, ideal para o cenário de um sistema de votação.

> Obs: Mesmo com isso, o sistema conta com uma simplicidade estrutural na identificação de eleitor e administrador
para manter a simplicidade do sistema.