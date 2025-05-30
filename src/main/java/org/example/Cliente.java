package org.example;

import org.example.Votacao.*;
import java.io.*;
import java.net.*;
import java.util.Scanner;
import com.google.protobuf.CodedInputStream;

public class Cliente {
    private static final String SERVIDOR = "localhost";
    private static final int PORTA_TCP = 12345;
    private static final String GRUPO_MULTICAST = "230.0.0.0";
    private static final int PORTA_MULTICAST = 6789;

    public static void main(String[] args) {
        new Thread(Cliente::receberNotas).start();

        try (
                Socket socket = new Socket(SERVIDOR, PORTA_TCP);
                InputStream entrada = socket.getInputStream();
                OutputStream saida = socket.getOutputStream();
                Scanner sc = new Scanner(System.in)
        ) {
            System.out.print("Digite seu nome: ");
            String nome = sc.nextLine();
            System.out.print("Você é admin? (s/n): ");
            boolean isAdmin = sc.nextLine().equalsIgnoreCase("s");

            LoginRequest.newBuilder()
                    .setUsername(nome)
                    .setIsAdmin(isAdmin)
                    .build().writeDelimitedTo(saida);

            LoginResponse resp = LoginResponse.parseDelimitedFrom(entrada);
            System.out.println(resp.getMessage());

            if (!resp.getSuccess()) return;

            if (isAdmin) {
                while (true) {
                    System.out.println("\n1- Adicionar candidato");
                    System.out.println("2- Remover candidato");
                    System.out.println("3- Enviar nota");
                    System.out.println("4- Sair");
                    String op = sc.nextLine();

                    if (op.equals("1")) {
                        System.out.print("Nome do candidato: ");
                        String nomeC = sc.nextLine();
                        AdminCommand.newBuilder()
                                .setAdd(AddCandidato.newBuilder().setNome(nomeC))
                                .build().writeDelimitedTo(saida);
                    } else if (op.equals("2")) {
                        System.out.print("ID do candidato: ");
                        String id = sc.nextLine();
                        AdminCommand.newBuilder()
                                .setRemove(RemoveCandidato.newBuilder().setId(id))
                                .build().writeDelimitedTo(saida);
                    } else if (op.equals("3")) {
                        System.out.print("Mensagem da nota: ");
                        String msg = sc.nextLine();
                        AdminCommand.newBuilder()
                                .setNota(Nota.newBuilder().setMensagem(msg))
                                .build().writeDelimitedTo(saida);
                    } else {
                        break;
                    }
                }
            } else {
                ListaCandidatos lista = ListaCandidatos.parseDelimitedFrom(entrada);
                System.out.println("\nCandidatos:");
                for (Candidato c : lista.getCandidatosList()) {
                    System.out.println(c.getId() + " - " + c.getNome());
                }

                System.out.print("Digite o ID do candidato: ");
                String id = sc.nextLine();

                VotoRequest.newBuilder()
                        .setCandidatoId(id)
                        .build().writeDelimitedTo(saida);

                VotoResponse votoResp = VotoResponse.parseDelimitedFrom(entrada);
                System.out.println(votoResp.getMessage());
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void receberNotas() {
        try (MulticastSocket socket = new MulticastSocket(PORTA_MULTICAST)) {
            InetAddress grupo = InetAddress.getByName(GRUPO_MULTICAST);
            socket.joinGroup(grupo);

            byte[] buffer = new byte[1024];
            while (true) {
                DatagramPacket pacote = new DatagramPacket(buffer, buffer.length);
                socket.receive(pacote);

                CodedInputStream codedInput = CodedInputStream.newInstance(
                        pacote.getData(), 0, pacote.getLength()
                );
                Nota nota = Nota.parseFrom(codedInput);

                System.out.println("\n📢 NOTA: " + nota.getMensagem());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
