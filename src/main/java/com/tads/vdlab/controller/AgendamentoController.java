package com.tads.vdlab.controller;

import com.tads.vdlab.controller.dto.AgendamentoDTO;
import com.tads.vdlab.controller.dto.UsuarioDTO;
import com.tads.vdlab.controller.validator.AgendamentoValidator;
import com.tads.vdlab.model.Agendamento;
import com.tads.vdlab.model.Role;
import com.tads.vdlab.model.Usuario;
import com.tads.vdlab.repository.AgendamentoRepository;
import com.tads.vdlab.repository.RoleRepository;
import com.tads.vdlab.repository.UsuarioRepository;
import com.tads.vdlab.service.UsuarioService;
import com.tads.vdlab.util.EmailUtil;
import com.tads.vdlab.util.UsuarioUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Controller
@RequestMapping("/agendamento")
public class AgendamentoController {

    @Value("${fpga.quantidadePlacas}")
    private Integer quantidadePlacas;

    private AgendamentoRepository repository;
    private UsuarioRepository usuarioRepository;
    private UsuarioService usuarioService;
    private EmailUtil emailUtil;

    @Autowired
    public AgendamentoController(JavaMailSender mailSender, AgendamentoRepository agendamentoRepository, UsuarioRepository usuarioRepository, UsuarioService usuarioService){
        this.repository = agendamentoRepository;
        this.usuarioRepository = usuarioRepository;
        this.usuarioService = usuarioService;
        emailUtil = new EmailUtil(mailSender);
    }

    @GetMapping
    public String agendamento(Model model,
                              @RequestParam("page") Optional<Integer> page,
                              @RequestParam("size") Optional<Integer> size){

        int currentPage = page.orElse(1);
        int pageSize = size.orElse(5);
        Page<UsuarioDTO> usuarioPage;
        boolean isBusca = model.containsAttribute("busca");
        String nomeBusca = model.containsAttribute("nomeBusca") ? (String) model.getAttribute("nomeBusca") : null;

        usuarioPage = usuarioService.findPaginated(PageRequest.of(currentPage - 1, pageSize), isBusca, nomeBusca);

        model.addAttribute("usuariosPage", usuarioPage);

        int totalPages = usuarioPage.getTotalPages();
        if (totalPages > 0) {
            List<Integer> pageNumbers = IntStream.rangeClosed(1, totalPages)
                    .boxed()
                    .collect(Collectors.toList());
            model.addAttribute("pageNumbers", pageNumbers);
        }

        if(model.getAttribute("agendamento") == null){
            model.addAttribute("agendamento", new Agendamento());
        }

        return "agendamento";
    }

    @GetMapping("/editar/{id}")
    public String editarAgendamento(@PathVariable("id") Long id, Model model){
        if(model.getAttribute("agendamento") == null){
            Agendamento agend = repository.getById(id);
            model.addAttribute("agendamento", AgendamentoDTO.toDTO(agend));
        }

        return "editarAgendamento";
    }

    @PostMapping("/editarAgendamento")
    public String editarAgendamento(@ModelAttribute Agendamento agendamento, BindingResult result, Principal principal, RedirectAttributes redirectAttributes){
        Usuario cadastrante = UsuarioUtil.getUsuarioLogado(principal, usuarioRepository);
        agendamento.setCadastrante(cadastrante);

        result = AgendamentoValidator.validarAgendamento(AgendamentoDTO.toDTO(agendamento), result,
                quantidadePlacas, repository);

        if(result.hasErrors()){
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.agendamento", result);
            AgendamentoDTO agend = AgendamentoDTO.toDTO(agendamento);
            if(agendamento.getUsuario() != null && agendamento.getUsuario().getNome() == null){
                Usuario u = usuarioRepository.findById(agendamento.getUsuario().getId()).get();
                agend.setUsuario(UsuarioDTO.toDTO(u));
            }
            redirectAttributes.addFlashAttribute("agendamento", agend);
            return "redirect:/agendamento/editar/"+agendamento.getId();
        }

        agendamento.setAtivo(true);
        repository.save(agendamento);

        redirectAttributes.addFlashAttribute("operacaoSucesso", true);
        return "redirect:/";
    }

    @GetMapping("/buscaUsuario")
    public String buscaUsuarioAgendamento(@RequestParam("nome") String busca, Model model, RedirectAttributes redirectAttributes){

        redirectAttributes.addFlashAttribute("nomeBusca", busca);
        redirectAttributes.addFlashAttribute("busca", true);
        return "redirect:/agendamento";
    }

    @PostMapping("/agendar")
    public String agendar(@ModelAttribute Agendamento agendamento, BindingResult result, Principal principal, RedirectAttributes redirectAttributes){
        Usuario cadastrante = UsuarioUtil.getUsuarioLogado(principal, usuarioRepository);
        agendamento.setCadastrante(cadastrante);

        result = AgendamentoValidator.validarAgendamento(AgendamentoDTO.toDTO(agendamento), result,
                quantidadePlacas, repository);

        if(result.hasErrors()){
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.agendamento", result);
            AgendamentoDTO agend = AgendamentoDTO.toDTO(agendamento);
            if(agendamento.getUsuario() != null && agendamento.getUsuario().getNome() == null){
                Usuario u = usuarioRepository.findById(agendamento.getUsuario().getId()).get();
                agend.setUsuario(UsuarioDTO.toDTO(u));
            }
            redirectAttributes.addFlashAttribute("agendamento", agend);
            return "redirect:/agendamento";
        }

        agendamento.setAtivo(true);
        repository.save(agendamento);

        Usuario u = usuarioRepository.findById(agendamento.getUsuario().getId()).get();
        new Thread(){
            @Override
            public void run() {
                emailUtil.newAgendamentoSendMail(agendamento.getDataAgendada(), u.getNome(), u.getEmail());
            }
        }.start();

        redirectAttributes.addFlashAttribute("operacaoSucesso", true);
        return "redirect:/agendamento";
    }

    @RequestMapping(value = "/deletar/{id}")
    public String deletarProduto(@PathVariable(name = "id") Long id, RedirectAttributes redirectAttributes) {
        Optional<Agendamento> agendamentoOptional =repository.findById(id);

        if (!agendamentoOptional.isPresent()) {
            return "redirect:/error";
        }

        Agendamento agendamento = agendamentoOptional.get();
        agendamento.setAtivo(false);
        repository.save(agendamento);

        redirectAttributes.addFlashAttribute("operacaoSucesso", true);
        return "redirect:/";
    }


}
