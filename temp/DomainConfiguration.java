
@Configuration
public class DomainConfiguration {

  @Bean
  public ApiClient apiClient(K8sProperties k8sProperties) throws IOException {
    Objects.requireNonNull(k8sProperties.getKubeConfig());
    Objects.requireNonNull(k8sProperties.getKubeConfig().getMode());
    Objects.requireNonNull(k8sProperties.getVerifySsl());

    K8sProperties.KubeConfigProperty kubeConfigProperty = k8sProperties.getKubeConfig();
    return switch (kubeConfigProperty.getMode()) {
      case IN_CLUSTER -> ClientBuilder
          .cluster()
          .setVerifyingSsl(k8sProperties.getVerifySsl())
          .build();
      case FILE -> {
        Objects.requireNonNull(kubeConfigProperty.getKubeConfigPath());

        FileReader configFileReader = new FileReader(kubeConfigProperty.getKubeConfigPath());
        yield ClientBuilder
            .kubeconfig(KubeConfig.loadKubeConfig(configFileReader))
            .setVerifyingSsl(k8sProperties.getVerifySsl())
            .build();
      }
    };
  }

  @Bean
  public K8sApiProvider k8sApiProvider(ApiClient apiClient) {
    return new K8sApiProvider(apiClient);
  }

  @Bean
  public ReconciliationService reconciliationService(SubjectResolver subjectResolver,
      DockerConfigJsonResolver dockerConfigJsonResolver, AipubProperties aipubProperties) {
    return new ReconciliationService(subjectResolver, dockerConfigJsonResolver,
        aipubProperties.getReservedNamespace());
  }

}