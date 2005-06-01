package net.kano.nully.plugin.psiToJimple;

public class JimpleBodyBuilderFactory extends AbstractJBBFactory {
    private InitialResolver initialResolver;

    public JimpleBodyBuilderFactory(InitialResolver initialResolver) {
        this.initialResolver = initialResolver;
    }

    public AbstractJimpleBodyBuilder createJimpleBodyBuilder(){
        return new JimpleBodyBuilder(initialResolver);
    }

}
