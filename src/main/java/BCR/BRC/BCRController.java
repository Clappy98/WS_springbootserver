package BCR.BRC;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class BCRController {

    @RequestMapping("/")
    public String showPage(){
        return "WasteServiceGUI";
    }
}
