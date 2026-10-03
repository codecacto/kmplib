plugins {
    id("kmplib.module")
}

// Motor de torneio: estrutura (round-robin, grupos, mata-mata), placar (pontos ou sets), formato de
// partida de raquete e classificação configurável. Domínio PURO — nenhuma dependência além da
// stdlib (sem Compose, sem Koin, sem datetime, sem persistência). Cada app guarda o torneio no seu
// próprio banco e desenha as suas telas; o que é igual entre eles (a regra) mora aqui.
