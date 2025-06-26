package pt.uc.sd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
public class messageSentOverWSIT {

    @Test
    public void guaranteeMessageSentOverWSTest(@Autowired MockMvc mvc, @Autowired MessagingController msgControl) throws Exception {
        // mvc.perform(get("/topics")).andExpect(status().isOk());
        //  .post().uri("/topics/messages").attribute("message", "Hello World").exchange();
        assertThat(msgControl).isNotNull();
        //FIXME: my-websocket should be a property in the Controller (and test could re-use)
        mvc.perform(get("/my-websocket")).andDo(print()).andExpect(status().isOk()).andExpect(content().string("Welcome to SockJS!\n"));

    }

}
