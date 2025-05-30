package org.example;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import org.example.Votacao.*;

public class Servidor {
    private static final int PORTA_TCP = 12345;
    private static final String GRUPO_MULTICAST = "230.0.0.0";
    private static final int PORTA_MULTICAST = 6789;
    private static final int TEMPO_VOTACAO_SEGUNDOS = 60;

    private static Map<String, CandidatoData> candidatos = new ConcurrentHashMap<>();
    private static Set<String> usuariosLogados = ConcurrentHashMap.newKeySet();
    private static volatile boolean votacaoAberta = true;

    public static void main(String[] args) {
        candidatos.put("1", new CandidatoData("1", "Candidato A"));
        candidatos.put("2", new CandidatoData("2", "Candidato B"));
        candidatos.put("3", new CandidatoData("3", "Candidato C"));

        try (ServerSocket serverSocket = new ServerSocket(PORTA_TCP)) {
            System.out.println("Servidor iniciado na porta " + PORTA_TCP);

            new Thread(() -> {
                try {
                    System.out.println("Votação aberta por " + TEMPO_VOTACAO_SEGUNDOS + " segundos.");
                    Thread.sleep(TEMPO_VOTACAO_SEGUNDOS * 5000);
                    votacaoAberta = false;
                    System.out.println("\n--- Votação encerrada ---");
                    exibirResultado();
                    System.exit(0);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();

            while (true) {
                Socket socket = serverSocket.accept();
                new Thread(new ClienteHandler(socket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class ClienteHandler implements Runnable {
        private Socket socket;

        public ClienteHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (
                    InputStream entrada = socket.getInputStream();
                    OutputStream saida = socket.getOutputStream()
            ) {
                LoginRequest login = LoginRequest.parseDelimitedFrom(entrada);

                if (login == null) return;

                synchronized (usuariosLogados) {
                    if (usuariosLogados.contains(login.getUsername())) {
                        LoginResponse.newBuilder()
                                .setSuccess(false)
                                .setMessage("Usuário já logado")
                                .build().writeDelimitedTo(saida);
                        socket.close();
                        return;
                    } else {
                        usuariosLogados.add(login.getUsername());
                        LoginResponse.newBuilder()
                                .setSuccess(true)
                                .setMessage("Login bem-sucedido")
                                .build().writeDelimitedTo(saida);
                    }
                }

                if (login.getIsAdmin()) {
                    tratarAdmin(entrada, saida);
                } else {
                    tratarEleitor(entrada, saida);
                }

                socket.close();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void tratarEleitor(InputStream entrada, OutputStream saida) throws IOException {
            ListaCandidatos.Builder lista = ListaCandidatos.newBuilder();
            for (CandidatoData c : candidatos.values()) {
                lista.addCandidatos(Candidato.newBuilder().setId(c.id).setNome(c.nome));
            }
            lista.build().writeDelimitedTo(saida);

            if (!votacaoAberta) {
                VotoResponse.newBuilder()
                        .setSuccess(false)
                        .setMessage("Votação encerrada")
                        .build().writeDelimitedTo(saida);
                return;
            }

            VotoRequest voto = VotoRequest.parseDelimitedFrom(entrada);

            if (voto != null && candidatos.containsKey(voto.getCandidatoId()) && votacaoAberta) {
                candidatos.get(voto.getCandidatoId()).votos++;
                VotoResponse.newBuilder()
                        .setSuccess(true)
                        .setMessage("Voto registrado")
                        .build().writeDelimitedTo(saida);
            } else {
                VotoResponse.newBuilder()
                        .setSuccess(false)
                        .setMessage("Voto inválido ou votação encerrada")
                        .build().writeDelimitedTo(saida);
            }
        }

        private void tratarAdmin(InputStream entrada, OutputStream saida) throws IOException {
            while (true) {
                AdminCommand cmd = AdminCommand.parseDelimitedFrom(entrada);
                if (cmd == null) break;

                if (cmd.hasAdd()) {
                    String id = String.valueOf(candidatos.size() + 1);
                    candidatos.put(id, new CandidatoData(id, cmd.getAdd().getNome()));
                    System.out.println("Admin adicionou candidato: " + cmd.getAdd().getNome());
                } else if (cmd.hasRemove()) {
                    candidatos.remove(cmd.getRemove().getId());
                    System.out.println("Admin removeu candidato: " + cmd.getRemove().getId());
                } else if (cmd.hasNota()) {
                    enviarNota(cmd.getNota().getMensagem());
                }
            }
        }
    }

    private static void enviarNota(String mensagem) {
        try (DatagramSocket socket = new DatagramSocket()) {
            InetAddress grupo = InetAddress.getByName(GRUPO_MULTICAST);
            Nota nota = Nota.newBuilder().setMensagem(mensagem).build();
            byte[] buffer = nota.toByteArray();
            DatagramPacket pacote = new DatagramPacket(buffer, buffer.length, grupo, PORTA_MULTICAST);
            socket.send(pacote);
            System.out.println("Nota enviada: " + mensagem);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void exibirResultado() {
        System.out.println("\n--- Resultado da Votação ---");
        int total = candidatos.values().stream().mapToInt(c -> c.votos).sum();

        CandidatoData vencedor = null;
        for (CandidatoData c : candidatos.values()) {
            double percentual = total > 0 ? (c.votos * 100.0) / total : 0;
            System.out.printf("%s - %d votos (%.2f%%)%n", c.nome, c.votos, percentual);
            if (vencedor == null || c.votos > vencedor.votos) {
                vencedor = c;
            }
        }

        if (vencedor != null) {
            System.out.println("Vencedor: " + vencedor.nome);
        } else {
            System.out.println("Nenhum voto registrado.");
        }
    }

    private static class CandidatoData {
        String id;
        String nome;
        int votos = 0;

        CandidatoData(String id, String nome) {
            this.id = id;
            this.nome = nome;
        }
    }
}
