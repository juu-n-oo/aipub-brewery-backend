@ConfigurationProperties(prefix = "app.k8s")
@Data
public class K8sProperties {

  @Nullable
  private Boolean verifySsl;
  @Nullable
  private KubeConfigProperty kubeConfig;


  public enum KubeConfigMode {

    IN_CLUSTER, FILE

  }

  @Data
  public static class KubeConfigProperty {

    @Nullable
    private KubeConfigMode mode;
    @Nullable
    private String kubeConfigPath;

  }

}